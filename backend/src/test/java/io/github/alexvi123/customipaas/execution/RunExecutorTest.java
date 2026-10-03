package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class RunExecutorTest {

	private static final class EchoConnector implements Connector {
		final List<String> received = new ArrayList<>();

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
			return "fail".equals(text) ? ActionResult.failure("echo refused") : ActionResult.success(Map.of("said", text));
		}
	}

	private final EchoConnector echo = new EchoConnector();
	private final RunStateService state = mock(RunStateService.class);
	private final JobQueue jobQueue = mock(JobQueue.class);
	private final RunExecutor executor = new RunExecutor(state,
			new ActionExecutor(new ConnectorRegistry(List.of(echo)), new InputValidator(JsonMapper.builder().build()),
					new CredentialsProvider(new MockEnvironment())),
			jobQueue);

	private final UUID runId = UUID.randomUUID();
	private final UUID step1 = UUID.randomUUID();
	private final UUID step2 = UUID.randomUUID();
	private final ClaimedJob job = new ClaimedJob(UUID.randomUUID(), runId);

	private void plan(Map<String, Object> firstInputs, Map<String, Object> secondInputs) {
		when(state.start(runId)).thenReturn(Optional.of(new RunPlan(runId, UUID.randomUUID(),
				Map.of("body", Map.of("name", "Alex")),
				List.of(new RunPlan.PlannedStep(step1, new StepDefinition("first", "echo", "say", firstInputs)),
						new RunPlan.PlannedStep(step2, new StepDefinition("second", "echo", "say", secondInputs))))));
	}

	@Test
	void runsStepsInOrderAndPassesOutputsForward() {
		plan(Map.of("text", "Hi {{trigger.body.name}}"), Map.of("text", "{{steps.first.output.said}}!"));

		executor.execute(job);

		assertThat(echo.received).containsExactly("Hi Alex", "Hi Alex!");
		InOrder order = inOrder(state, jobQueue);
		order.verify(state).finishStep(eq(step1), eq(StepStatus.SUCCEEDED), eq(Map.of("said", "Hi Alex")), isNull());
		order.verify(state).finishStep(eq(step2), eq(StepStatus.SUCCEEDED), any(), isNull());
		order.verify(state).finishRun(runId, RunStatus.SUCCEEDED, null);
		order.verify(jobQueue).delete(job.id());
	}

	@Test
	void aFailedStepFailsTheRunAndStopsThere() {
		plan(Map.of("text", "fail"), Map.of("text", "never"));

		executor.execute(job);

		assertThat(echo.received).containsExactly("fail");
		verify(state).finishStep(eq(step1), eq(StepStatus.FAILED), any(), eq("echo refused"));
		verify(state, never()).startStep(eq(step2), any());
		verify(state).finishRun(runId, RunStatus.FAILED, "Step 'first' failed: echo refused");
		verify(jobQueue).delete(job.id());
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
		verify(jobQueue).delete(job.id());
	}
}
