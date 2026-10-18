package net.weavemc.installer;

import java.io.IOException;

/**
 * A network failure with a message written for the user. The technical cause is
 * attached as a suppressed exception (not as the cause) so that the UI's
 * root-cause lookup stops here and shows the readable message, while the log
 * still records the full stack trace.
 */
final class ReleaseServerException extends IOException {
    private static final long serialVersionUID = 1L;

    ReleaseServerException(String message, Throwable technicalCause) {
        super(message);
        if (technicalCause != null) {
            addSuppressed(technicalCause);
        }
    }
}
