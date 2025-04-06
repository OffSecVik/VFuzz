package vfuzz.except;

public class RequestBuildingException extends RuntimeException {
  public RequestBuildingException() {
    super();
  }

  public RequestBuildingException(String message) {
    super(message);
  }

  public RequestBuildingException(String message, Throwable cause) {
    super(message, cause);
  }
}
