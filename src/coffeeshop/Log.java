package coffeeshop;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** One log line per event: time, endpoint of the peer, message. Thread-safe. */
final class Log {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {}

    static synchronized void info(String endpoint, String msg) {
        System.out.println(LocalTime.now().format(TIME) + " [" + endpoint + "] " + msg);
    }

    static void info(String msg) {
        info("server", msg);
    }

    static void error(String endpoint, String msg) {
        info(endpoint, "ERROR " + msg);
    }
}