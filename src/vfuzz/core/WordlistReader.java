package vfuzz.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code WordlistReader} class provides functionality to read and iterate through a wordlist file.
 * The wordlist is loaded into memory when the class is instantiated, and it can be accessed in a thread-safe
 * manner by retrieving each word one by one using the {@link #getNextPayload()} method.
 *
 * <p>This class supports multithreaded environments by using an {@link AtomicInteger} to keep track of
 * the current index being accessed from the wordlist.
 */
public class WordlistReader {
    private static List<String> wordlist;
    private final AtomicInteger currentIndex = new AtomicInteger(0);


    /**
     * Constructs a {@code WordlistReader} and loads the wordlist from the specified file path.
     *
     * <p>The wordlist is loaded once and shared across all instances of the class. The file is read line-by-line
     * into an immutable list, ensuring that it cannot be modified once loaded.
     *
     * @param path The path to the wordlist file. Must not be null or empty.
     * @throws IllegalArgumentException If the path is null, empty, or invalid.
     * @throws RuntimeException If an I/O error occurs while reading the wordlist.
     */
    public WordlistReader(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        synchronized (WordlistReader.class) {
            if (wordlist == null) {
                try {
                    wordlist = Collections.unmodifiableList(Files.readAllLines(Paths.get(path)));
                } catch (InvalidPathException ipe) {
                    throw new IllegalArgumentException("Invalid path provided: " + path);
                } catch (IOException ie) {
                    throw new RuntimeException("Failed to read wordlist from path: " + path, ie);
                }
            }
        }
    }

    /**
     * Retrieves the next payload from the wordlist.
     *
     * <p>Each call increments the index and returns the word at the current position. When the end of the wordlist
     * is reached, this method returns {@code null}.
     *
     * @return The next payload from the wordlist, or {@code null} if the end of the wordlist is reached.
     */
    public String getNextPayload() {
        int index = currentIndex.getAndIncrement();
        return index < wordlist.size() ? wordlist.get(index) : null;
    }

    public int getWordlistSize() {
        return wordlist.size();
    }
}