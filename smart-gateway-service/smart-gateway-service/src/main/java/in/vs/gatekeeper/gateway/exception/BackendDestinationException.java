package in.vs.gatekeeper.gateway.exception;

public class BackendDestinationException extends RuntimeException {

    public BackendDestinationException(String message) {
        super(message);
    }

    public BackendDestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}