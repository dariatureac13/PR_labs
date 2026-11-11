package coffeeshop;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test tool for the defense: starts many clients at once, each makes one random order.
 * Usage: java coffeeshop.Bots <host> <port> <count> [abrupt]
 * "abrupt": every bot kills its connection (TCP reset, no QUIT) right after its order was accepted.
 */
public final class Bots {
    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int count = Integer.parseInt(args[2]);
        boolean abrupt = args.length > 3 && args[3].equalsIgnoreCase("abrupt");
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final int id = i;
            Thread t = new Thread(() -> bot(id, host, port, abrupt));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
    }

    private static void bot(int id, String host, int port, boolean abrupt) {
        try (Socket s = new Socket(host, port)) {
            s.setSoTimeout(120_000);
            OutputStream out = s.getOutputStream();
            LineReader in = new LineReader(s.getInputStream(), 4096);
            in.readLine();                                  // welcome
            String order = randomOrder();
            out.write(("ORDER " + order + "\n").getBytes(StandardCharsets.UTF_8));
            say(id, "-> ORDER " + order);
            while (true) {
                String line = in.readLine();
                if (line == null) { say(id, "server closed"); return; }
                say(id, "<- " + line);
                if (line.startsWith("ACCEPTED") && abrupt) {
                    s.setSoLinger(true, 0);                  // close() will send RST instead of FIN
                    say(id, "killing the connection");
                    return;
                }
                if (line.startsWith("READY") || line.startsWith("REFUSED") || line.startsWith("ERR")) break;
            }
            out.write("QUIT\n".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            say(id, "error: " + e);
        }
    }

    private static String randomOrder() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Product> ps = new ArrayList<>(List.of(Product.values()));
        Collections.shuffle(ps);
        StringBuilder sb = new StringBuilder();
        for (int i = 0, k = 1 + r.nextInt(3); i < k; i++) {
            sb.append(i > 0 ? " " : "").append(ps.get(i).label()).append('=').append(1 + r.nextInt(3));
        }
        return sb.toString();
    }

    private static synchronized void say(int id, String msg) {
        System.out.println("bot-" + id + " " + msg);
    }
}