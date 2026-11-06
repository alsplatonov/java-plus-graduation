package ewm.client;

import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.exception.ValidationException;

final class FallbackSupport {

    private FallbackSupport() {
    }

    // Бизнес-ошибки (404/409/400) пробрасываются вызывающем
    static void rethrowBusinessException(Throwable cause) {
        if (cause instanceof NotFoundException e) {
            throw e;
        }
        if (cause instanceof ConflictException e) {
            throw e;
        }
        if (cause instanceof ValidationException e) {
            throw e;
        }
    }
}
