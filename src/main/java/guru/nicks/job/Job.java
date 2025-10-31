package guru.nicks.job;

/**
 * Job.
 */
@FunctionalInterface
public interface Job extends Runnable {

    /**
     * Defines whether multiple jobs having the same {@link #getName()} are allowed to run in parallel.
     *
     * @return default implementation returns {@code false}, for safety
     */
    default boolean allowConcurrentExecution() {
        return false;
    }

    /**
     * Returns job name to be used in logs, statistics, and for concurrency limits.
     *
     * @return job name; default implementation returns the class name of the job
     */
    default String getName() {
        return getClass().getName();
    }

}
