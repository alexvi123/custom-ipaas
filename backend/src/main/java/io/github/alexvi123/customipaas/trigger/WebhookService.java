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
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns an incoming webhook into a pending run + its step runs + a queued job, all in one transaction. A delivery
 * with an already-seen dedupe key returns the existing run instead.
 */
@Service
public class WebhookService {

	private final WorkflowRepository workflows;
	private final WorkflowVersionRepository versions;
	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final JobQueue jobQueue;
	private final TransactionTemplate transactions;

	public WebhookService(WorkflowRepository workflows, WorkflowVersionRepository versions, RunRepository runs,
			StepRunRepository stepRuns, JobQueue jobQueue, TransactionTemplate transactions) {
		this.workflows = workflows;
		this.versions = versions;
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.jobQueue = jobQueue;
		this.transactions = transactions;
	}

	/** @param dedupeKey the sender's Idempotency-Key, or null */
	public WebhookResult accept(String token, Map<String, Object> trigger, String dedupeKey) {
		try {
			return transactions.execute(tx -> create(token, trigger, dedupeKey));
		} catch (DataIntegrityViolationException e) {
			// A parallel delivery with the same key committed first; its transaction won, ours rolled back.
			if (dedupeKey == null) {
				throw e;
			}
			return transactions.execute(tx -> existing(activeWorkflow(token), dedupeKey).orElseThrow(() -> e));
		}
	}

	private WebhookResult create(String token, Map<String, Object> trigger, String dedupeKey) {
		Workflow workflow = activeWorkflow(token);
		Optional<WebhookResult> duplicate = existing(workflow, dedupeKey);
		if (duplicate.isPresent()) {
			return duplicate.get();
		}
		WorkflowVersion version = versions.findByWorkflowIdAndVersion(workflow.getId(), workflow.getCurrentVersion())
				.orElseThrow();
		List<StepDefinition> steps = WorkflowDefinition.fromStored(version.getDefinition()).steps();

		Run run = runs.save(new Run(workflow.getId(), version.getId(), trigger, dedupeKey, Instant.now()));
		stepRuns.saveAll(IntStream.range(0, steps.size())
				.mapToObj(i -> new StepRun(run.getId(), steps.get(i).key(), i, steps.get(i).connector(), steps.get(i).action()))
				.toList());
		runs.flush(); // the run row must exist before the job references it; also surfaces a dedupe race here
		jobQueue.enqueue(run.getId());
		return new WebhookResult(run.getId(), false);
	}

	private Workflow activeWorkflow(String token) {
		Workflow workflow = workflows.findByWebhookToken(token).orElseThrow(WebhookNotFoundException::new);
		if (!workflow.isActive()) {
			throw new WorkflowInactiveException(workflow.getName());
		}
		return workflow;
	}

	private Optional<WebhookResult> existing(Workflow workflow, String dedupeKey) {
		if (dedupeKey == null) {
			return Optional.empty();
		}
		return runs.findByWorkflowIdAndDedupeKey(workflow.getId(), dedupeKey)
				.map(run -> new WebhookResult(run.getId(), true));
	}
}
