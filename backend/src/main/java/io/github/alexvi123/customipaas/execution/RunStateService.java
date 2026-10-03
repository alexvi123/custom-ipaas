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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunStateService {

	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final WorkflowVersionRepository versions;

	public RunStateService(RunRepository runs, StepRunRepository stepRuns, WorkflowVersionRepository versions) {
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.versions = versions;
	}

	/** Marks the run RUNNING and returns its plan, or empty if it must not run (gone, or left over from a crash). */
	@Transactional
	public Optional<RunPlan> start(UUID runId) {
		Optional<Run> found = runs.findById(runId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Run run = found.get();
		List<StepRun> steps = stepRuns.findByRunIdOrderByPosition(runId);
		if (run.getStatus() != RunStatus.PENDING) {
			if (!run.getStatus().isFinished()) {
				run.finish(RunStatus.FAILED, "The worker was interrupted before the run finished", Instant.now());
				skipUnfinished(steps);
			}
			return Optional.empty();
		}

		run.start(Instant.now());
		WorkflowDefinition definition = WorkflowDefinition.fromStored(
				versions.findById(run.getWorkflowVersionId()).orElseThrow().getDefinition());
		Map<String, StepDefinition> byKey = new HashMap<>();
		definition.steps().forEach(step -> byKey.put(step.key(), step));
		List<RunPlan.PlannedStep> planned = steps.stream()
				.map(step -> new RunPlan.PlannedStep(step.getId(), byKey.get(step.getStepKey())))
				.toList();
		return Optional.of(new RunPlan(run.getId(), run.getWorkflowId(), run.getTriggerPayload(), planned));
	}

	@Transactional
	public void startStep(UUID stepRunId, Map<String, Object> input) {
		stepRuns.findById(stepRunId).orElseThrow().start(InputRedactor.redact(input), Instant.now());
	}

	@Transactional
	public void finishStep(UUID stepRunId, StepStatus status, Map<String, Object> output, String error) {
		stepRuns.findById(stepRunId).orElseThrow().finish(status, output, error, Instant.now());
	}

	@Transactional
	public void finishRun(UUID runId, RunStatus status, String error) {
		runs.findById(runId).orElseThrow().finish(status, error, Instant.now());
		if (status == RunStatus.FAILED) {
			skipUnfinished(stepRuns.findByRunIdOrderByPosition(runId));
		}
	}

	private static void skipUnfinished(List<StepRun> steps) {
		steps.stream().filter(step -> step.getStatus() == StepStatus.PENDING || step.getStatus() == StepStatus.RUNNING)
				.forEach(StepRun::skip);
	}
}
