#@disabled
Feature: Job Executor Service
  Job Executor Service should execute jobs and handle exceptions

  Scenario: Job is executed successfully
    Given a job that completes successfully
    When the job is executed
    Then no exception should be thrown
    And the job should be marked as not running after completion

  Scenario: Job execution fails
    Given a job that throws an exception
    When the job is executed
    Then the exception message should contain "Job 'TestJob' failed"
    And the job should be marked as not running after completion

  Scenario: Job execution fails with alerts disabled
    Given a job that throws an exception
    When the job is executed
    Then the exception message should contain "Job 'TestJob' failed"
    And the job should be marked as not running after completion

  Scenario: Concurrent execution is disallowed
    Given a job that disallows concurrent execution
    And the job is already running
    When the job is executed
    Then the exception message should contain "Job 'TestJob' disallows concurrent execution"

  Scenario Outline: Job execution with different configurations
    Given a job with name "<jobName>" that <jobOutcome>
    And the job <concurrencyConfig>
    When the job is executed
    Then the job execution should <expectedResult>
    Examples:
      | jobName     | jobOutcome             | concurrencyConfig              | expectedResult              |
      | SimpleJob   | completes successfully | allows concurrent execution    | complete without exceptions |
      | ComplexJob  | throws an exception    | allows concurrent execution    | fail with an exception      |
      | CriticalJob | completes successfully | disallows concurrent execution | complete without exceptions |

  # New scenarios to test JobExecutionException

  Scenario: JobExecutionException contains the failed job reference
    Given a job that throws an exception
    When the job is executed
    Then a JobExecutionException should be thrown

  Scenario: JobExecutionException preserves the original cause
    Given a job that throws a NullPointerException with message "Critical data missing"
    When the job is executed
    Then a JobExecutionException should be thrown
    And the exception should have NullPointerException as its cause
    And the cause should have the message "Critical data missing"
