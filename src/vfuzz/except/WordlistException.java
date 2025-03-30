package vfuzz.except;

public class WordlistException extends Exception {
    public WordlistException() {
        super();
    }

    public WordlistException(String message) {
        super(message);
    }

    public WordlistException(String message, Throwable cause) {
        super(message, cause);
    }
}