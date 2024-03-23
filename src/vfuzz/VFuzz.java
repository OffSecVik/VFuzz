package vfuzz;

import java.io.IOException;

public class VFuzz {
    public static void main(String[] args) {

        String wordlistPath = "C:\\Users\\Vik\\Downloads\\subdomains-top1million-20000.txt";


        try {
            ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, "http://127.0.0.1:5000/", 1);
            orchestrator.startFuzzing();
        } catch (IOException ie) {
            System.out.println("orchestartor took shit.");
        }
    }
}