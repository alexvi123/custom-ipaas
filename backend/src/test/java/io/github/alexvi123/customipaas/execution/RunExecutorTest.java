package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionExecutor;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.Connector;
import io.github.alexvi123.customipaas.connector.ConnectorInfo;
import io.github.alexvi123.customipaas.connector.ConnectorRegistry;
import io.github.alexvi123.customipaas.connector.CredentialsProvider;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.InputValidator;
import io.github.alexvi123.customipaas.workflow.StepDefinition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class RunExecutorTest {

	/** Says its text back; "fail" is a permanent failure, "flaky" a temporary one. */
	private static final class EchoConnector implements Connector {
		final List<String> received = new CopyOnWriteArrayList<>();

		@Override
		public ConnectorInfo info() {
			return new ConnectorInfo("echo", "Echo", "Echo");
		}

		@Override
		public List<ActionDefinition> actions() {
			return List.of(new ActionDefinition("say", "Say", "Say",
					List.of(FieldDefinition.text("text", "Text").asRequired())));
		}

		@Override
		public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
			String text = input.requireString("text");
			received.add(text);
			return switch (text) {
				case "fail" -> ActionResult.failure("echo refused");
				case "flaky" -> ActionResult.retryableFailure("echo is busy");
				default -> ActionResult.success(Map.of("said", text));
			};
		}
	}

	private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
	private static final Duration DELAY = Duration.ofSeconds(10);

	private final EchoConnector echo = new EchoConnector();
	private final RunStateService state = mock(RunStateService.class);
	private final JobQueue jobQueue = mock(JobQueue.class);
	private final Backoff backoff = mock(Backoff.class);
	private final RunExecutor executor = new RunExecutor(state,
			new ActionExecutor(new ConnectorRegistry(List.of(echo)), new InputValidator(JsonMapper.builder().build()),
					new CredentialsProvider(new MockEnvironment())),
			jobQueue, backoff,
			new ExecutionProperties(3, Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofSeconds(5)),
			Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

	private final UUID runId = UUID.randomUUID();
	private final UUID step1 = UUID.randomUUID();
	private final UUID step2 = UUID.randomUUID();
	private final ClaimedJob job = new ClaimedJob(UUID.randomUUID(), runId, 1);

	@BeforeEach
	void leaseIsHeld() {
		when(jobQueue.extendLease(eq(job), any())).thenReturn(true);
		when(backoff.delayAfter(anyInt())).thenReturn(DELAY);
	}

	private static RunPlan.PlannedStep pending(UUID id, StepDefinition definition) {
		return new RunPlan.PlannedStep(id, definition, StepStatus.PENDING, 0, null);
	}

	private void plan(RunPlan.PlannedStep... steps) {
		when(state.start(runId)).thenReturn(Optional.of(new RunPlan(runId, UUID.randomUUID(),
				Map.of("body", Map.of("name", "Alex")), List.of(steps))));
	}

	private void plan(Map<String, Object> firstInputs, Map<String, Object> secondInputs) {
		plan(pending(step1, new StepDefinition("first", "echo", "say", firstInputs)),
				pending(step2, new StepDefinition("second", "echo", "say", secondInputs)));
	}

	@Test
	void runsStepsInOrderAndPassesOutputsForward() {
		plan(Map.of("text", "Hi {{trigger.body.name}}"), Map.of("text", "{{steps.first.output.said}}!"));

		executor.execute(job);

		assertThat(echo.received).containsExactly("Hi Alex", "Hi Alex!");
		InOrder order = inOrder(state, jobQueue);
		order.verify(state).startStep(eq(step1), any(), eq(1));
		order.verify(state).finishStep(eq(step1), eq(StepStatus.SUCCEEDED), eq(Map.of("said", "Hi Alex")), isNull());
		order.verify(state).finishStep(eq(step2), eq(StepStatus.SUCCEEDED), any(), isNull());
		order.verify(state).finishRun(runId, RunStatus.SUCCEEDED, null);
		order.verify(jobQueue).delete(job);
	}

	@Test
	void aPermanentFailureFailsTheRunAtOnce() {
		plan(Map.of("text", "fail"), Map.of("text", "never"));

		executor.execute(job);

		assertThat(echo.received).containsExactly("fail");
		verify(state).finishStep(eq(step1), eq(StepStatus.FAILED), any(), eq("echo refused"));
		verify(state, never()).startStep(eq(step2), any(), anyInt());
		verify(state).finishRun(runId, RunStatus.FAILED, "Step 'first' failed: echo refused");
		verify(jobQueue).delete(job);
	}

	@Test
	void aTemporaryFailureSchedulesARetryInsteadOfFailing() {
		plan(Map.of("text", "flaky"), Map.of("text", "never"));

		executor.execute(job);

		Instant retryAt = NOW.plus(DELAY);
		verify(backoff).delayAfter(1);
		verify(state).waitForRetry(eq(step1), any(), eq("echo is busy"), eq(retryAt));
		verify(jobQueue).reschedule(job, retryAt);
		verify(state, never()).finishRun(any(), any(), any());
		verify(jobQueue, never()).delete(any());
		assertThat(echo.received).containsExactly("flaky");
	}

	@Test
	void retriesStopAtMaxAttempts() {
		plan(new RunPlan.PlannedStep(step1, new StepDefinition("first", "echo", "say", Map.of("text", "flaky")),
				StepStatus.WAITING_RETRY, 2, Map.of()));

		executor.execute(job);

		verify(state).startStep(eq(step1), any(), eq(3));
		verify(state).finishStep(eq(step1), eq(StepStatus.FAILED), any(), eq("echo is busy"));
		verify(state).finishRun(runId, RunStatus.FAILED, "Step 'first' failed: echo is busy (after 3 attempts)");
		verify(jobQueue, never()).reschedule(any(), any());
	}

	@Test
	void aStepCanAllowFewerAttemptsThanTheDefault() {
		plan(pending(step1, new StepDefinition("first", "echo", "say", Map.of("text", "flaky"), 1, null)));

		executor.execute(job);

		verify(state).finishRun(runId, RunStatus.FAILED, "Step 'first' failed: echo is busy (after 1 attempts)");
		verify(jobQueue, never()).reschedule(any(), any());
	}

	@Test
	void aResumedRunSkipsSucceededStepsAndReusesTheirOutput() {
		plan(new RunPlan.PlannedStep(step1, new StepDefinition("first", "echo", "say", Map.of("text", "never")),
						StepStatus.SUCCEEDED, 1, Map.of("said", "Hi Alex")),
				new RunPlan.PlannedStep(step2,
						new StepDefinition("second", "echo", "say", Map.of("text", "{{steps.first.output.said}}!")),
						StepStatus.RUNNING, 1, null));

		executor.execute(job);

		assertThat(echo.received).containsExactly("Hi Alex!");
		verify(state, never()).startStep(eq(step1), any(), anyInt());
		verify(state).startStep(eq(step2), any(), eq(2));
		verify(state).finishRun(runId, RunStatus.SUCCEEDED, null);
	}

	@Test
	void aStepInterruptedTooOftenIsGivenUp() {
		plan(new RunPlan.PlannedStep(step1, new StepDefinition("first", "echo", "say", Map.of("text", "hi")),
				StepStatus.RUNNING, 3, null));

		executor.execute(job);

		assertThat(echo.received).isEmpty();
		verify(state).finishStep(eq(step1), eq(StepStatus.FAILED), isNull(), any());
		verify(state).finishRun(eq(runId), eq(RunStatus.FAILED), any());
	}

	@Test
	void aWorkerThatLostItsLeaseStopsWithoutTouchingTheJob() {
		plan(Map.of("text", "hi"), Map.of("text", "never"));
		when(jobQueue.extendLease(eq(job), any())).thenReturn(false);

		executor.execute(job);

		assertThat(echo.received).isEmpty();
		verify(state, never()).finishRun(any(), any(), any());
		verify(jobQueue, never()).delete(any());
		verify(jobQueue, never()).reschedule(any(), any());
	}

	@Test
	void aMissingTemplateValueFailsTheStep() {
		plan(Map.of("text", "{{trigger.body.missing}}"), Map.of("text", "never"));

		executor.execute(job);

		assertThat(echo.received).isEmpty();
		verify(state).finishStep(step1, StepStatus.FAILED, null, "Template {{trigger.body.missing}} has no value");
		verify(state).finishRun(eq(runId), eq(RunStatus.FAILED), any());
	}

	@Test
	void aRunThatMustNotRunOnlyRemovesTheJob() {
		when(state.start(runId)).thenReturn(Optional.empty());

		executor.execute(job);

		verify(state, never()).finishRun(any(), any(), any());
		verify(jobQueue).delete(job);
	}
}
