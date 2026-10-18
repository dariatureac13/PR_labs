package lab1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Random;

/**
 * Data generation and cutting
 *   java lab1.DataTool gen   <count> <out-file>
 *   java lab1.DataTool split <in-file> <out-dir> <parts> <lines-per-part>
 */
public class DataTool {
    public static void main(String[] a) throws Exception {
        if (a.length >= 3 && a[0].equals("gen")) {
            long n = Long.parseLong(a[1]);
            Random r = new Random(12345);
            try (BufferedWriter w = new BufferedWriter(new FileWriter(a[2]), 1 << 20)) {
                for (long i = 0; i < n; i++) {
                    w.write(Integer.toString(r.nextInt(2_000_001) - 1_000_000));
                    w.write('\n');
                }
            }
        } else if (a.length >= 5 && a[0].equals("split")) {
            int parts = Integer.parseInt(a[3]);
            long per = Long.parseLong(a[4]);
            try (BufferedReader br = new BufferedReader(new FileReader(a[1]), 1 << 20)) {
                for (int p = 1; p <= parts; p++) {
                    try (BufferedWriter w = new BufferedWriter(new FileWriter(a[2] + "/numbers_" + p + ".txt"), 1 << 20)) {
                        for (long i = 0; i < per; i++) {
                            String line = br.readLine();
                            if (line == null) throw new IllegalStateException("not enough lines");
                            w.write(line);
                            w.write('\n');
                        }
                    }
                }
            }
        } else {
            System.out.println("gen <count> <out> | split <in> <dir> <parts> <lines-per-part>");
        }
    }
}
