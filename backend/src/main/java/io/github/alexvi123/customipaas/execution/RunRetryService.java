package io.github.alexvi123.customipaas.execution;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Retry" on a failed run: back to the queue, continuing from the failed step. Succeeded steps are kept. */
@Service
public class RunRetryService {

	private final RunRepository runs;
	private final StepRunRepository stepRuns;
	private final JobQueue jobQueue;

	public RunRetryService(RunRepository runs, StepRunRepository stepRuns, JobQueue jobQueue) {
		this.runs = runs;
		this.stepRuns = stepRuns;
		this.jobQueue = jobQueue;
	}

	@Transactional
	public Run retry(UUID runId) {
		// The row lock makes a double click safe: the second request waits, then sees PENDING and gets a 409.
		Run run = runs.findByIdForUpdate(runId).orElseThrow(() -> new RunNotFoundException(runId));
		if (run.getStatus() != RunStatus.FAILED) {
			throw new RunNotRetryableException(run.getStatus());
		}
		run.requeue();
		stepRuns.findByRunIdOrderByPosition(runId).stream()
				.filter(step -> step.getStatus() != StepStatus.SUCCEEDED)
				.forEach(StepRun::reset);
		runs.flush();
		jobQueue.enqueue(runId);
		return run;
	}
}
