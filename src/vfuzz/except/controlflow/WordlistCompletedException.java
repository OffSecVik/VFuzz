package vfuzz.except.controlflow;

public class WordlistCompletedException extends Exception {
    public WordlistCompletedException() {
        super();
    }

    public WordlistCompletedException(String message) {
        super(message);
    }

    public WordlistCompletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
