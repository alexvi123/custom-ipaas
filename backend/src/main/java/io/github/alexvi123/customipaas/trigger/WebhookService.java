package io.github.alexvi123.customipaas.trigger;

import io.github.alexvi123.customipaas.execution.JobQueue;
import io.github.alexvi123.customipaas.execution.Run;
import io.github.alexvi123.customipaas.execution.RunRepository;
import io.github.alexvi123.customipaas.execution.StepRun;
import io.github.alexvi123.customipaas.execution.StepRunRepository;
import io.github.alexvi123.customipaas.workflow.StepDefinition;
import io.github.alexvi123.customipaas.workflow.Workflow;
import io.github.alexvi123.customipaas.workflow.WorkflowDefinition;
import io.github.alexvi123.customipaas.workflow.WorkflowRepository;
import io.github.alexvi123.customipaas.workflow.WorkflowVersion;
import io.github.alexvi123.customipaas.workflow.WorkflowVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns an incoming webhook into a pending run + its step runs + a queued job, all in one transaction. */
@Service
public class WebhookService {

	private final WorkflowRepository workflows;
	private final WorkflowVersionRepository versions;
	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final JobQueue jobQueue;

	public WebhookService(WorkflowRepository workflows, WorkflowVersionRepository versions, RunRepository runs,
			StepRunRepository stepRuns, JobQueue jobQueue) {
		this.workflows = workflows;
		this.versions = versions;
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.jobQueue = jobQueue;
	}

	@Transactional
	public UUID accept(String token, Map<String, Object> trigger) {
		Workflow workflow = workflows.findByWebhookToken(token).orElseThrow(WebhookNotFoundException::new);
		if (!workflow.isActive()) {
			throw new WorkflowInactiveException(workflow.getName());
		}
		WorkflowVersion version = versions.findByWorkflowIdAndVersion(workflow.getId(), workflow.getCurrentVersion())
				.orElseThrow();
		List<StepDefinition> steps = WorkflowDefinition.fromStored(version.getDefinition()).steps();

		Run run = runs.save(new Run(workflow.getId(), version.getId(), trigger, Instant.now()));
		stepRuns.saveAll(IntStream.range(0, steps.size())
				.mapToObj(i -> new StepRun(run.getId(), steps.get(i).key(), i, steps.get(i).connector(), steps.get(i).action()))
				.toList());
		runs.flush();
		jobQueue.enqueue(run.getId());
		return run.getId();
	}
}
