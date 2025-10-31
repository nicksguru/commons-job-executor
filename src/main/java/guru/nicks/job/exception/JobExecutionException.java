package guru.nicks.job.exception;

import lombok.experimental.StandardException;

/**
 * Exception thrown when a job execution fails (the cause, if any, is the original exception thrown by the job code).
 */
@StandardException
public class JobExecutionException extends RuntimeException {
}
