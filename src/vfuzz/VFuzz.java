package vfuzz;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class VFuzz {
    public static void main(String[] args) {

        String wordlistPath = "C:\\Users\\Vik\\Downloads\\subdomains-top1million-20000.txt\\";


        try {
            ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, "http://127.0.0.1:8000/", 20);
            orchestrator.startFuzzing();
            orchestrator.awaitCompletion();
        } catch (IOException ie) {
            System.out.println("orchestartor took shit.");
        }  catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Execution was interrupted.");
        }

    }
}