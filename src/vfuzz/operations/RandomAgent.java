package vfuzz.operations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomAgent {


    static Random random = new Random();
    static List<String> lines = new ArrayList<>();

    static {
        InputStream inputStream = RandomAgent.class.getResourceAsStream("RandomAgents.txt");

        if (inputStream == null) {
            System.out.println("RandomAgents.txt was not found");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            System.out.println("Failed reading RandomAgents.txt: " + e.getMessage());
        }
    }


        public static String get() {
            return lines.get(random.nextInt(lines.size()));
        }
}
