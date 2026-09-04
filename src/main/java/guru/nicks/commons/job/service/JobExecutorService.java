package guru.nicks.commons.job.service;

import guru.nicks.commons.job.Job;
import guru.nicks.commons.job.exception.JobExecutionException;
import guru.nicks.commons.log.domain.LogContext;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Job executor - with statistics, concurrency, and error reporting.
 */
public interface JobExecutorService {

    /**
     * Runs the given job.  Spring scheduler reuses its threads for unrelated tasks. For this reason, upon job
     * completion - no matter successful or not - this method clears all {@link LogContext} entries to avoid such
     * leftovers as current username (the jobs are supposed to run asynchronously). For the same reason,
     * {@link SecurityContextHolder#clearContext()} is called.
     *
     * @param job job to execute
     * @throws JobExecutionException if {@link Job#allowConcurrentExecution()} is {@code false}, but another job with
     *                               the same {@link Job#getName()} is already running - or if the job failed, and the
     *                               cause is the exception thrown by the job
     */
    void execute(Job job);

    /**
     * Puts {@link Authentication} to Spring Security context before executing a job. Default implementation does
     * nothing.
     */
    default void setJobAuthentication() {
        // do nothing
    }

    /**
     * Does something to react to a job failure. Default implementation throws a {@link JobExecutionException}.
     *
     * @param job failed job
     * @param e   exception thrown by the job
     */
    default void reactToJobFailure(Job job, Exception e) {
        throw new JobExecutionException("Job '" + job.getName() + "' failed: " + e.getMessage(), e);
    }

}
