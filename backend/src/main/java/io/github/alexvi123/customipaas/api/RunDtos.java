package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.execution.Run;
import io.github.alexvi123.customipaas.execution.RunQueryService.RunWithSteps;
import io.github.alexvi123.customipaas.execution.RunStatus;
import io.github.alexvi123.customipaas.execution.StepRun;
import io.github.alexvi123.customipaas.execution.StepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RunDtos {

	private RunDtos() {
	}

	private static Long millisBetween(Instant startedAt, Instant finishedAt) {
		return startedAt == null || finishedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
	}

	record RunSummary(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) UUID workflowId,
			@Schema(requiredMode = REQUIRED) RunStatus status,
			String error,
			@Schema(requiredMode = REQUIRED) Instant createdAt,
			Instant startedAt,
			Instant finishedAt,
			Long durationMs) {

		static RunSummary from(Run run) {
			return new RunSummary(run.getId(), run.getWorkflowId(), run.getStatus(), run.getError(), run.getCreatedAt(),
					run.getStartedAt(), run.getFinishedAt(), millisBetween(run.getStartedAt(), run.getFinishedAt()));
		}
	}

	record StepRunView(
			@Schema(requiredMode = REQUIRED) String key,
			@Schema(requiredMode = REQUIRED) int position,
			@Schema(requiredMode = REQUIRED) String connector,
			@Schema(requiredMode = REQUIRED) String action,
			@Schema(requiredMode = REQUIRED) StepStatus status,
			Map<String, Object> input,
			Map<String, Object> output,
			String error,
			Instant startedAt,
			Instant finishedAt,
			Long durationMs) {

		static StepRunView from(StepRun step) {
			return new StepRunView(step.getStepKey(), step.getPosition(), step.getConnectorKey(), step.getActionKey(),
					step.getStatus(), step.getInput(), step.getOutput(), step.getError(), step.getStartedAt(),
					step.getFinishedAt(), millisBetween(step.getStartedAt(), step.getFinishedAt()));
		}
	}

	record RunDetails(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) UUID workflowId,
			@Schema(requiredMode = REQUIRED) int workflowVersion,
			@Schema(requiredMode = REQUIRED) RunStatus status,
			String error,
			@Schema(requiredMode = REQUIRED) Map<String, Object> trigger,
			@Schema(requiredMode = REQUIRED) Instant createdAt,
			Instant startedAt,
			Instant finishedAt,
			Long durationMs,
			@Schema(requiredMode = REQUIRED) List<StepRunView> steps) {

		static RunDetails from(RunWithSteps loaded) {
			Run run = loaded.run();
			return new RunDetails(run.getId(), run.getWorkflowId(), loaded.workflowVersion(), run.getStatus(),
					run.getError(), run.getTriggerPayload(), run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(),
					millisBetween(run.getStartedAt(), run.getFinishedAt()),
					loaded.steps().stream().map(StepRunView::from).toList());
		}
	}
}
