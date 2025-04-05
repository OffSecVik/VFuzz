package vfuzz.except;

public class MalformedRequestException extends Exception {
    private String malformedURI;

    public MalformedRequestException() {
        super();
    }

    public MalformedRequestException(String message) {
        super(message);
    }

    public MalformedRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedRequestException(String malformedUri, String message, Throwable cause) {
        super(message, cause);
        this.malformedURI = malformedUri;
    }

    public String getMalformedURI() {
        return malformedURI;
    }
}
