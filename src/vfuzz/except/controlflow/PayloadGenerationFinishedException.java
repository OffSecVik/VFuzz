package vfuzz.except.controlflow;

public class PayloadGenerationFinishedException extends Exception {

    public PayloadGenerationFinishedException() {
        super();
    }

    public PayloadGenerationFinishedException(String message) {
        super(message);
    }

    public PayloadGenerationFinishedException(String message, Throwable cause) {
        super(message, cause);
    }
}
