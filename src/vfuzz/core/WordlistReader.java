package vfuzz.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WordlistReader {
    private static List<String> wordlist;
    private final AtomicInteger currentIndex = new AtomicInteger(0);

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

    public String getNextPayload() {
        int index = currentIndex.getAndIncrement();
        return index < wordlist.size() ? wordlist.get(index) : null;
    }

    public boolean hasMorePayloads() {
        return currentIndex.get() < wordlist.size();
    }
}