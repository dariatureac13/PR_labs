package coffeeshop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Interactive console program for both roles.
 * Usage: java coffeeshop.Client <host> <port> [client|provider]
 *
 * Two threads: the main thread reads the keyboard (blocking) and sends; the "receiver" thread
 * blocks on the socket and prints every message the moment it arrives. This is why a READY or an
 * ALERT appears while you are typing: the keyboard read and the socket read never wait for each other.
 */
public final class Client {
    private static OutputStream out;
    private static final Object OUT_LOCK = new Object();
    private static final Object SCREEN_LOCK = new Object();

    public static void main(String[] args) throws Exception {
        String host;
        int port;
        boolean provider;
        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
            provider = args.length > 2 && args[2].equalsIgnoreCase("provider");
        } catch (RuntimeException e) {
            System.err.println("Usage: java coffeeshop.Client <host> <port> [client|provider]");
            return;
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
        } catch (IOException e) {
            System.err.println("Can not connect to " + host + ":" + port + ": " + e.getMessage());
            return;
        }
        socket.setKeepAlive(true);
        socket.setSoTimeout(45_000);                       // the server answers our PINGs, silence = dead server
        out = socket.getOutputStream();

        Thread receiver = new Thread(() -> receive(socket), "receiver");
        receiver.setDaemon(true);
        receiver.start();

        Thread pinger = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(15_000);
                    send("PING");
                }
            } catch (InterruptedException ignored) {
            }
        }, "pinger");
        pinger.setDaemon(true);
        pinger.start();

        if (provider) send("ROLE PROVIDER");

        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        prompt();
        String line;
        while ((line = keyboard.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("exit")) line = "QUIT";
            if (line.isEmpty()) {
                prompt();
                continue;
            }
            send(line);
            if (line.equalsIgnoreCase("QUIT")) {
                receiver.join(2000);
                break;
            }
        }
        send("QUIT");                                      // keyboard EOF (Ctrl-D / Ctrl-Z)
        Thread.sleep(300);
        socket.close();
    }

    private static void send(String line) {
        synchronized (OUT_LOCK) {
            try {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                show("Can not send: " + e.getMessage(), false);
            }
        }
    }

    private static void receive(Socket socket) {
        try {
            LineReader reader = new LineReader(socket.getInputStream(), 4096);
            while (true) {
                try {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (!line.equals("PONG")) show(line, true);
                } catch (LineReader.LineTooLongException e) {
                    show("(ignored a too long message from the server)", true);
                }
            }
            show("Server closed the connection.", false);
        } catch (SocketTimeoutException e) {
            show("The server does not answer. Disconnecting.", false);
        } catch (IOException e) {
            show("Connection lost: " + e.getMessage(), false);
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
            System.exit(0);                                // also wakes the main thread blocked on the keyboard
        }
    }

    private static void show(String msg, boolean withPrompt) {
        synchronized (SCREEN_LOCK) {
            System.out.print("\r" + msg + "\n" + (withPrompt ? "> " : ""));
            System.out.flush();
        }
    }

    private static void prompt() {
        synchronized (SCREEN_LOCK) {
            System.out.print("> ");
            System.out.flush();
        }
    }
}