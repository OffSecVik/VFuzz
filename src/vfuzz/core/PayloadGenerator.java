package vfuzz.core;

import vfuzz.except.controlflow.PayloadGenerationFinishedException;

import java.util.ArrayList;
import java.util.List;

public class PayloadGenerator {

    List<WordlistReader> wordlistReaders;
    List<String> payloads;

    public PayloadGenerator(List<WordlistReader> wordlistReaders) {
        this.wordlistReaders = wordlistReaders;
        this.payloads = new ArrayList<>();
    }

    public List<String> generatePayloads() throws PayloadGenerationFinishedException {
        if (payloads.isEmpty()) {
            for (WordlistReader wordlistReader : wordlistReaders) {
                String payload = wordlistReader.getNextPayload();
                payloads.add(payload);
            }
            return payloads;
        }

        setPayloadsForAllWordlistReaders(wordlistReaders.size() - 1);
        return payloads;
    }

    private void setPayloadsForAllWordlistReaders(int index) throws PayloadGenerationFinishedException {
        if (index < 0) {
            throw new PayloadGenerationFinishedException();
        }
        String nextPayload = getPayloadFromWordlistReader(index);
        if (nextPayload == null) {
            resetWordlistReaderAt(index);
            nextPayload = getPayloadFromWordlistReader(index);
            setPayloadsForAllWordlistReaders(index - 1);
        }
        payloads.set(index, nextPayload);
    }

    private String getPayloadFromWordlistReader(int index) {
        return wordlistReaders.get(index).getNextPayload();
    }

    private void resetWordlistReaderAt(int index) {
        wordlistReaders.get(index).reset();
    }

    public static void main(String[]args) {
        try {
            WordlistReader w1 = new WordlistReader("C:\\Users\\vikto\\IdeaProjects\\VFuzz\\bin\\vfuzz\\payloadGeneratorTest");
            WordlistReader w2 = new WordlistReader("C:\\Users\\vikto\\IdeaProjects\\VFuzz\\bin\\vfuzz\\payloadGeneratorTest2");
            WordlistReader w3 = new WordlistReader("C:\\Users\\vikto\\IdeaProjects\\VFuzz\\bin\\vfuzz\\cheat");

            List<WordlistReader> w = new ArrayList<>();
            w.add(w1);
            w.add(w2);
            w.add(w3);


            PayloadGenerator p = new PayloadGenerator(w);

            while (true) {
                List<String> payloads = p.generatePayloads();
                System.out.println(payloads);
            }

        } catch (Exception ignored) {
        }
    }
}
