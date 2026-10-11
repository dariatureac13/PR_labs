package lab1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class DataGenerator {
    private static final int TOTAL_NUMBERS = 50_000_000;
    private static final int NUM_FILES = 8;
    private static final int NUMBERS_PER_FILE = TOTAL_NUMBERS / NUM_FILES; // 6,250,000 numbers per file

    public static void main(String[] args) throws IOException {
        System.out.println("Generating " + TOTAL_NUMBERS + " numbers...");
        File mainFile = new File("numbers.txt");
        Random random = new Random();

        // Generate the main file and split it into 8 parts simultaneously
        try (BufferedWriter mainWriter = new BufferedWriter(new FileWriter(mainFile))) {
            for (int i = 0; i < NUM_FILES; i++) {
                File partFile = new File("part_" + (i + 1) + ".txt");
                try (BufferedWriter partWriter = new BufferedWriter(new FileWriter(partFile))) {
                    for (int j = 0; j < NUMBERS_PER_FILE; j++) {
                        // Generate random integer between -1,000,000 and 1,000,000
                        int num = random.nextInt(2_000_001) - 1_000_000;
                        String line = num + "\n";
                        mainWriter.write(line);
                        partWriter.write(line);
                    }
                }
            }
        }
        System.out.println("Data files successfully generated!");
    }
}