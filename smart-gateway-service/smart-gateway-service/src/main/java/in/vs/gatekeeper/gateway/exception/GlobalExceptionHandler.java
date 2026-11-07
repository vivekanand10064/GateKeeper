
package in.vs.gatekeeper.gateway.exception;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BackendDestinationException.class)
    public ResponseEntity<Void> handleBackendDestination(
            BackendDestinationException exception) {

        log.warn("Backend destination rejected: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Void> handleBackendConnection(
            ResourceAccessException exception) {

        if (isTimeoutException(exception)) {
            log.warn("Backend request timed out", exception);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        }

        log.error("Backend connection failed", exception);

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleInvalidArgument(
            IllegalArgumentException exception) {

        log.warn("Invalid gateway request: {}", exception.getMessage());

        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleUnexpectedException(
            Exception exception) {

        log.error("Unexpected gateway error", exception);

        return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
        ).build();
    }

    private boolean isTimeoutException(Throwable exception) {

        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
