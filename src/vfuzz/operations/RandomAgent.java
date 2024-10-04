package vfuzz.operations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The {@code RandomAgent} class provides functionality to return a random user-agent string
 * from a predefined list stored in the "RandomAgents.txt" file. This class is useful in
 * situations where user-agents need to be randomized to avoid detection or to simulate different browsers.
 *
 * <p>The user-agents are loaded into memory upon class initialization from the "RandomAgents.txt" file.
 * The class uses a {@link Random} object to randomly select a user-agent from the list when requested.
 */
public class RandomAgent {

    static final Random random = new Random();
    static final List<String> lines = new ArrayList<>();

    static {
        InputStream inputStream = RandomAgent.class.getResourceAsStream("RandomAgents.txt");

        if (inputStream == null) {
            System.out.println("RandomAgents.txt was not found");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            System.out.println("Failed reading RandomAgents.txt: " + e.getMessage());
        }
    }

    /**
     * Returns a randomly selected user-agent string from the list.
     *
     * @return A randomly selected user-agent string.
     */
    public static String get() {
        return lines.get(random.nextInt(lines.size()));
    }
}
