package vfuzz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WordlistReader {
    private static List<String> wordlist; // static wordlist because we only use one wordlist
    private AtomicInteger currentIndex = new AtomicInteger(0);

    public WordlistReader(String path) {
        synchronized (WordlistReader.class) { // ensure that only one thread can initialize wordlist at a time
            if (wordlist == null) {

                try {
                    wordlist = Collections.unmodifiableList(Files.readAllLines(Paths.get(path))); // check if we already have a wordlist to avoid unnecessary file IO
                } catch (IOException ie) {
                    System.out.println("Wordlist reader took a shit.");
                }
            }
        }

    }

    public String getNextPayload() {
        int index = currentIndex.getAndIncrement();
        return index < wordlist.size() ? wordlist.get(index) : null;
    }

    // For recursion, reset the index or create a new WordlistReader instance
}