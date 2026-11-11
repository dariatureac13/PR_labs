package coffeeshop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Usage: java coffeeshop.Server <port> [idleSeconds=60]   (idleSeconds 0 = never drop silent peers) */
public final class Server {
    public static void main(String[] args) throws IOException {
        int port, idle = 60;
        try {
            port = Integer.parseInt(args[0]);
            if (args.length > 1) idle = Integer.parseInt(args[1]);
        } catch (RuntimeException e) {
            System.err.println("Usage: java coffeeshop.Server <port> [idleSeconds]");
            return;
        }
        Shop shop = new Shop();
        try (ServerSocket listener = new ServerSocket()) {      // the LISTENING socket: only accepts
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(port), 128);
            Log.info("listening on port " + port + " (idle timeout " + idle + " s)");
            while (true) {
                Socket s;
                try {
                    s = listener.accept();                      // returns a NEW connected socket per client
                } catch (IOException e) {
                    Log.error("server", "accept failed: " + e);
                    if (listener.isClosed()) break;
                    sleep(100);
                    continue;
                }
                try {
                    new Connection(s, shop, idle).start();
                } catch (IOException e) {
                    Log.error("server", "can not start connection: " + e);
                    try { s.close(); } catch (IOException ignored) { }
                }
            }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}