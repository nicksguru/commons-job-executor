package guru.nicks.commons.job.impl;

import guru.nicks.commons.job.Job;
import guru.nicks.commons.job.exception.JobExecutionException;
import guru.nicks.commons.job.service.JobExecutorService;
import guru.nicks.commons.log.domain.LogContext;
import guru.nicks.commons.utils.LockUtils;

import am.ik.yavi.meta.ConstraintArguments;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotBlank;
import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;

/**
 * Runs jobs.
 *
 * @see #execute(Job)
 */
@Slf4j
public abstract class AbstractJobExecutorServiceImpl implements JobExecutorService {

    protected static final String JOB_ALREADY_RUNNING_MESSAGE =
            "Job '%s' disallows concurrent execution; it was already started at %s and has been running for %s";

    /**
     * @see #formatJobDuration(Job)
     */
    protected static final String JOB_DURATION_FORMAT = "%.1fs";

    /**
     * For each job instance running (possibly concurrently), contains a timestamp of its start.
     */
    @Getter(AccessLevel.PROTECTED)
    private final Map<Job, Instant> jobStartInstant = new ConcurrentHashMap<>();

    /**
     * Lock to protect concurrent access to {@link #getJobStartInstant()} during concurrency constraint checks.
     */
    private final StampedLock jobStartLock = new StampedLock();


    @ConstraintArguments
    @Override
    public void execute(Job job) {
        checkNotNull(job, _AbstractJobExecutorServiceImplExecuteArgumentsMeta.JOB.name());
        checkNotBlank(job.getName(), _AbstractJobExecutorServiceImplExecuteArgumentsMeta.JOB.name() + ".name");

        LogContext.JOB_NAME.put(job.getName());
        checkConcurrencyConstraints(job);

        try {
            markJobAsRunning(job);
            // Spring Security Context is thread-local, so no collisions will occur in parallel jobs if some day
            // they need a different security context.
            setJobAuthentication();

            log.info("Job '{}' started", job.getName());
            job.run();
            log.info("Job '{}' completed ({} elapsed)", job.getName(), formatJobDuration(job));
        } catch (Exception e) {
            reactToJobFailure(job, e);
        } finally {
            // clear ALL entries to avoid such leftovers as current username
            LogContext.clearAll();
            // theoretically, this may fail, therefore the log context is cleared above to avoid such leftovers
            markJobAsNotRunning(job);
        }
    }

    /**
     * Validates, in a thread-safe manner, that the job can be executed without violating concurrency constraints.
     * <p>
     * Also, to avoid race conditions (two threads trying to start the same job), this method - only for jobs NOT
     * allowing concurrent execution - atomically stores current time as the job's start time in
     * {@link #getJobStartInstant()} if it's not already there.
     *
     * @param job job to check
     * @throws JobExecutionException if job is already running and doesn't allow concurrent execution
     */
    protected void checkConcurrencyConstraints(Job job) {
        if (job.allowConcurrentExecution()) {
            return;
        }

        LockUtils.runWithExclusiveLock(jobStartLock, () -> {
            // this method is atomic or not depending on the Map implementation
            Instant existingStart = jobStartInstant.putIfAbsent(job, Instant.now());

            if (existingStart != null) {
                String message = String.format(Locale.US, JOB_ALREADY_RUNNING_MESSAGE,
                        job.getName(), existingStart, formatJobDurationFromInstant(existingStart));
                throw new JobExecutionException(message);
            }
        });
    }

    /**
     * Calculates job duration from the start time.
     *
     * @param startInstant job start time
     * @return job duration formatted with {@link #JOB_DURATION_FORMAT}, or 'unknown' if the start time is {@code null}
     */
    protected String formatJobDurationFromInstant(Instant startInstant) {
        if (startInstant == null) {
            return "unknown";
        }

        Duration duration = Duration.between(startInstant, Instant.now());
        return String.format(Locale.US, JOB_DURATION_FORMAT, duration.toMillis() / 1000.0);
    }

    /**
     * Stores current time in {@link #getJobStartInstant()} for jobs allowing concurrent execution. For jobs NOT
     * allowing concurrent execution, {@link #checkConcurrencyConstraints(Job)} has already done this.
     *
     * @param job job to mark as running
     */
    protected void markJobAsRunning(Job job) {
        if (job.allowConcurrentExecution()) {
            jobStartInstant.put(job, Instant.now());
        }
        // else: already set by checkConcurrencyConstraints
    }

    /**
     * Removes, in a thread-safe manner, the entry from {@link #getJobStartInstant()} for the given job.
     *
     * @param job job to mark as not running
     */
    protected void markJobAsNotRunning(Job job) {
        LockUtils.runWithExclusiveLock(jobStartLock,
                () -> jobStartInstant.remove(job));
    }

    /**
     * Formats job duration according to {@value #JOB_DURATION_FORMAT}.
     *
     * @param job job
     * @return job duration formatted as string
     */
    protected String formatJobDuration(Job job) {
        return calculateJobDuration((job))
                .map(jobDuration -> String.format(Locale.US, JOB_DURATION_FORMAT, jobDuration.toMillis() / 1000.0))
                .orElse("<job not running>");
    }

    /**
     * Calculates job duration.
     *
     * @param job job
     * @return job duration (if job isn't marked as running, returns empty optional)
     */
    protected Optional<Duration> calculateJobDuration(Job job) {
        return Optional.of(job)
                .map(jobStartInstant::get)
                .map(instant -> Duration.between(instant, Instant.now()));
    }

}
