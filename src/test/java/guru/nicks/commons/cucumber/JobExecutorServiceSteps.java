package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.job.Job;
import guru.nicks.commons.job.exception.JobExecutionException;
import guru.nicks.commons.job.impl.AbstractJobExecutorServiceImpl;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Step definitions for testing {@link AbstractJobExecutorServiceImpl}.
 */
@RequiredArgsConstructor
public class JobExecutorServiceSteps {

    // DI
    private final TextWorld textWorld;

    private AbstractJobExecutorServiceImpl jobExecutorService;
    private TestJob testJob;
    private Map<Job, Instant> jobStartInstant;

    @Before
    public void beforeEachScenario() {
        jobExecutorService = new AbstractJobExecutorServiceImpl() {
        };
        jobStartInstant = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(jobExecutorService, "jobStartInstant", jobStartInstant);

        // reset auth possibly left from previous tests
        SecurityContextHolder.clearContext();
    }

    @After
    public void afterEachScenario() {
        SecurityContextHolder.clearContext();
    }

    @Given("a job that completes successfully")
    public void aJobThatCompletesSuccessfully() {
        testJob = TestJob.builder()
                .name("TestJob")
                .allowConcurrentExecution(true)
                .throwException(false)
                .build();
    }

    @Given("a job that throws an exception")
    public void aJobThatThrowsAnException() {
        testJob = TestJob.builder()
                .name("TestJob")
                .allowConcurrentExecution(true)
                .throwException(true)
                .build();
    }

    @Given("a job that disallows concurrent execution")
    public void aJobThatDisallowsConcurrentExecution() {
        testJob = TestJob.builder()
                .name("TestJob")
                .allowConcurrentExecution(false)
                .throwException(false)
                .build();
    }

    @Given("the job is already running")
    public void theJobIsAlreadyRunning() {
        jobStartInstant.put(testJob, Instant.now().minusSeconds(60));
    }

    @Given("a job with name {string} that completes successfully")
    public void aJobWithNameThatCompletesSuccessfully(String jobName) {
        testJob = TestJob.builder()
                .name(jobName)
                .allowConcurrentExecution(true)
                .throwException(false)
                .build();
    }

    @Given("a job with name {string} that throws an exception")
    public void aJobWithNameThatThrowsAnException(String jobName) {
        testJob = TestJob.builder()
                .name(jobName)
                .allowConcurrentExecution(true)
                .throwException(true)
                .build();
    }

    @Given("the job allows concurrent execution")
    public void theJobAllowsConcurrentExecution() {
        testJob.setAllowConcurrentExecution(true);
    }

    @Given("the job disallows concurrent execution")
    public void theJobDisallowsConcurrentExecution() {
        testJob.setAllowConcurrentExecution(false);
    }

    @When("the job is executed")
    public void theJobIsExecuted() {
        try {
            jobExecutorService.execute(testJob);
        } catch (Exception e) {
            textWorld.setLastException(e);
        }
    }

    @Then("the job should be marked as not running after completion")
    public void theJobShouldBeMarkedAsNotRunningAfterCompletion() {
        assertThat(jobStartInstant.containsKey(testJob))
                .as("job still running")
                .isFalse();
    }

    @Then("the job execution should complete without exceptions")
    public void theJobExecutionShouldCompleteWithoutExceptions() {
        assertThat(textWorld.getLastException())
                .as("last exception")
                .isNull();

        assertThat(jobStartInstant.containsKey(testJob))
                .as("job still running")
                .isFalse();
    }

    @Then("the job execution should fail with an exception")
    public void theJobExecutionShouldFailWithAnException() {
        assertThat(textWorld.getLastException())
                .as("last exception")
                .isNotNull();

        assertThat(jobStartInstant.containsKey(testJob))
                .as("job still running")
                .isFalse();
    }


    @Then("a JobExecutionException should be thrown")
    public void verifyJobExecutionExceptionThrown() {
        assertThat(textWorld.getLastException()).isInstanceOf(JobExecutionException.class);
    }

    @Then("the exception should have NullPointerException as its cause")
    public void verifyExceptionCauseType() {
        JobExecutionException jobException = (JobExecutionException) textWorld.getLastException();
        assertThat(jobException.getCause()).as("jobException.getCause()").isNotNull();
        assertThat(jobException.getCause()).isInstanceOf(NullPointerException.class);
    }

    @Then("the cause should have the message {string}")
    public void verifyCauseMessage(String expectedMessage) {
        JobExecutionException jobException = (JobExecutionException) textWorld.getLastException();
        assertThat(jobException.getCause().getMessage()).as("cause.getMessage()").isEqualTo(expectedMessage);
    }

    @Given("a job that throws a NullPointerException with message {string}")
    public void jobThrowsSpecificException(String message) {
        testJob = spy(TestJob.builder()
                .name("TestJob")
                .allowConcurrentExecution(true)
                .throwException(true)
                .build());
        // WARNING: for 'spy' objects, use doReturn/doThrow instead of 'when'!
        doThrow(new NullPointerException(message)).when(testJob).run();
    }

    /**
     * Test implementation of {@link Job} interface.
     */
    @Builder
    private static class TestJob implements Job {

        @Getter(onMethod_ = @Override)
        private final String name;

        private final boolean throwException;

        @Setter
        private boolean allowConcurrentExecution;

        @Override
        public boolean allowConcurrentExecution() {
            return allowConcurrentExecution;
        }

        @Override
        public void run() {
            if (throwException) {
                throw new RuntimeException("Test exception");
            }
        }

    }

}
