package io.github.alexvi123.customipaas.execution;

import io.github.alexvi123.customipaas.workflow.WorkflowVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** History lists and run details. */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

	public record RunWithSteps(Run run, int workflowVersion, List<StepRun> steps) {
	}

	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final WorkflowVersionRepository versions;

	public RunQueryService(RunRepository runs, StepRunRepository stepRuns, WorkflowVersionRepository versions) {
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.versions = versions;
	}

	/** Newest first; pass the last item's createdAt as {@code before} to get the next page. */
	public List<Run> list(UUID workflowId, Instant before, int limit) {
		Instant cursor = before == null ? Instant.now().plusSeconds(1) : before;
		return runs.findByWorkflowIdAndCreatedAtBeforeOrderByCreatedAtDesc(workflowId, cursor,
				Limit.of(Math.clamp(limit, 1, 100)));
	}

	public RunWithSteps get(UUID runId) {
		Run run = runs.findById(runId).orElseThrow(() -> new RunNotFoundException(runId));
		int version = versions.findById(run.getWorkflowVersionId()).orElseThrow().getVersion();
		return new RunWithSteps(run, version, stepRuns.findByRunIdOrderByPosition(runId));
	}
}
