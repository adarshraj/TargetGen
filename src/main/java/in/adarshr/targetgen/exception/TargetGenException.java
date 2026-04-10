package in.adarshr.targetgen.exception;

public class TargetGenException extends RuntimeException {
    public TargetGenException(String message) {
        super(message);
    }

    public TargetGenException(String message, Throwable cause) {
        super(message, cause);
    }
}
