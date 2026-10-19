package io.github.alexvi123.customipaas.execution;

import io.github.alexvi123.customipaas.workflow.StepDefinition;
import io.github.alexvi123.customipaas.workflow.WorkflowDefinition;
import io.github.alexvi123.customipaas.workflow.WorkflowVersionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunStateService {

	private static final Logger log = LoggerFactory.getLogger(RunStateService.class);

	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final WorkflowVersionRepository versions;

	public RunStateService(RunRepository runs, StepRunRepository stepRuns, WorkflowVersionRepository versions) {
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.versions = versions;
	}

	/**
	 * Marks a pending run RUNNING (or picks up a RUNNING one: after a retry delay or a crash) and returns its plan.
	 * Empty when the run is gone or already finished.
	 */
	@Transactional
	public Optional<RunPlan> start(UUID runId) {
		Optional<Run> found = runs.findById(runId);
		if (found.isEmpty() || found.get().getStatus().isFinished()) {
			return Optional.empty();
		}
		Run run = found.get();
		if (run.getStatus() == RunStatus.PENDING) {
			run.start(Instant.now());
		} else {
			log.info("Resuming run {}", runId);
		}

		WorkflowDefinition definition = WorkflowDefinition.fromStored(
				versions.findById(run.getWorkflowVersionId()).orElseThrow().getDefinition());
		Map<String, StepDefinition> byKey = new HashMap<>();
		definition.steps().forEach(step -> byKey.put(step.key(), step));
		List<RunPlan.PlannedStep> planned = stepRuns.findByRunIdOrderByPosition(runId).stream()
				.map(step -> new RunPlan.PlannedStep(step.getId(), byKey.get(step.getStepKey()), step.getStatus(),
						step.getAttempt(), step.getOutput()))
				.toList();
		return Optional.of(new RunPlan(run.getId(), run.getWorkflowId(), run.getTriggerPayload(), planned));
	}

	@Transactional
	public void startStep(UUID stepRunId, Map<String, Object> input, int attempt) {
		stepRuns.findById(stepRunId).orElseThrow().start(InputRedactor.redact(input), attempt, Instant.now());
	}

	@Transactional
	public void finishStep(UUID stepRunId, StepStatus status, Map<String, Object> output, String error) {
		stepRuns.findById(stepRunId).orElseThrow().finish(status, output, error, Instant.now());
	}

	@Transactional
	public void waitForRetry(UUID stepRunId, Map<String, Object> output, String error, Instant retryAt) {
		stepRuns.findById(stepRunId).orElseThrow().waitForRetry(output, error, retryAt, Instant.now());
	}

	@Transactional
	public void finishRun(UUID runId, RunStatus status, String error) {
		runs.findById(runId).orElseThrow().finish(status, error, Instant.now());
		if (status == RunStatus.FAILED) {
			stepRuns.findByRunIdOrderByPosition(runId).stream()
					.filter(step -> step.getStatus() == StepStatus.PENDING || step.getStatus() == StepStatus.RUNNING)
					.forEach(StepRun::skip);
		}
	}
}
