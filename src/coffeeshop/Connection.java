package coffeeshop;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One accepted TCP connection (client or provider). Two threads per connection:
 *  - reader thread: reads lines, executes commands (only this thread ever reads the socket);
 *  - writer thread: the ONLY thread that writes to the socket. Everybody else (workers,
 *    other client threads) calls send(), which just puts the line in a queue.
 * So a worker never writes at the same time as the reader thread, lines never interleave,
 * and a slow or dead peer can not block a worker or another client.
 */
final class Connection {
    static final int MAX_LINE = 256;
    private static final String POISON = new String("<close>");   // compared by identity

    final String endpoint;
    private final Socket socket;
    private final Shop shop;
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>(1000);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Set<Order> orders = ConcurrentHashMap.newKeySet();   // not yet READY
    private volatile boolean provider;
    private volatile boolean hasOrdered;

    Connection(Socket socket, Shop shop, int idleSeconds) throws IOException {
        this.socket = socket;
        this.shop = shop;
        this.endpoint = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        socket.setKeepAlive(true);              // the OS probes a silent peer
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(idleSeconds * 1000); // 0 = no timeout; clients send PING every 15 s
    }

    void start() {
        new Thread(this::writeLoop, "writer-" + endpoint).start();
        new Thread(this::readLoop, "reader-" + endpoint).start();
    }

    // ---------------------------------------------------------------- output side

    /** Non-blocking: queue a message for the peer. Safe to call from any thread. */
    void send(String line) {
        if (closing.get()) return;
        if (!outbox.offer(line)) {
            Log.error(endpoint, "peer does not read its messages, dropping the connection");
            closeNow();
        }
    }

    private void writeLoop() {
        try {
            OutputStream out = socket.getOutputStream();
            while (true) {
                String m = outbox.take();
                if (m == POISON) break;
                out.write((m + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            if (!closing.get()) Log.error(endpoint, "write failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeNow();
        }
    }

    /** Close after everything already queued was written. */
    void closeAfterFlush() {
        if (closing.getAndSet(true)) return;
        if (!outbox.offer(POISON)) closeNow();
    }

    void closeNow() {
        closing.set(true);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        outbox.offer(POISON);
    }

    // ---------------------------------------------------------------- orders of this connection

    void addOrder(Order o) {
        hasOrdered = true;
        orders.add(o);
    }

    void forget(Order o) {
        orders.remove(o);
    }

    List<Order> orders() {
        return new ArrayList<>(orders);
    }

    // ---------------------------------------------------------------- input side

    private void readLoop() {
        Log.info(endpoint, "CONNECTED");
        try {
            LineReader reader = new LineReader(socket.getInputStream(), MAX_LINE);
            send("OK welcome to the coffee shop, type HELP");
            while (true) {
                String line;
                try {
                    line = reader.readLine();
                } catch (LineReader.LineTooLongException e) {
                    error("message too long (max " + MAX_LINE + " bytes)");
                    continue;
                }
                if (line == null) {
                    Log.info(endpoint, "DISCONNECTED (peer closed the connection)");
                    break;
                }
                if (!handle(line)) {
                    Log.info(endpoint, "DISCONNECTED (QUIT)");
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            if (!closing.get()) Log.error(endpoint, "no data from the peer (idle timeout), dropping it");
            closeNow();
        } catch (IOException e) {
            if (!closing.get()) Log.error(endpoint, "connection lost: " + e.getMessage());
            closeNow();
        } catch (Throwable t) {                           // a bug must never kill the server
            Log.error(endpoint, "unexpected failure: " + t);
            closeNow();
        } finally {
            shop.connectionClosed(this);                   // cancel orders, release the provider role
            closeAfterFlush();
        }
    }

    private void error(String msg) {
        send("ERR " + msg);
        Log.error(endpoint, msg);
    }

    /** Executes one command. Returns false when the connection must end. */
    private boolean handle(String line) {
        if (line.isBlank()) {
            error("empty command");
            return true;
        }
        String[] p = line.trim().split("\\s+");
        String cmd = p[0].toUpperCase(Locale.ROOT);
        try {
            switch (cmd) {
                case "HELP" -> send("OK commands: MENU | ORDER <product>=<qty> ... | DELIVER <product>=<qty> ... (provider only)"
                        + " | ROLE PROVIDER | PING | QUIT");
                case "MENU" -> send(shop.menu());
                case "PING" -> send("PONG");
                case "QUIT" -> {
                    send("BYE");
                    return false;
                }
                case "ORDER" -> {
                    if (provider) error("the provider can not order");
                    else shop.order(this, Shop.parseItems(p));
                }
                case "DELIVER" -> {
                    if (!provider) error("only the provider can deliver products");
                    else shop.deliver(this, Shop.parseItems(p));
                }
                case "ROLE" -> {
                    if (p.length != 2) error("usage: ROLE PROVIDER");
                    else if (!p[1].equalsIgnoreCase("PROVIDER")) error("unknown role '" + Shop.safe(p[1]) + "'");
                    else if (provider) error("you are already the provider");
                    else if (hasOrdered) error("a client that has ordered can not become the provider");
                    else if (!shop.registerProvider(this)) error("a provider is already connected");
                    else provider = true;
                }
                default -> error("unknown command '" + Shop.safe(p[0]) + "', type HELP");
            }
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
        return true;
    }
}