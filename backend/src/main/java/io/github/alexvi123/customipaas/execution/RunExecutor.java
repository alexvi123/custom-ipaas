package io.github.alexvi123.customipaas.execution;

import io.github.alexvi123.customipaas.connector.ActionExecutor;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import io.github.alexvi123.customipaas.workflow.StepDefinition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs the steps of one claimed job in order, skipping steps that already succeeded. No transaction is open here:
 * each status change is its own short transaction in {@link RunStateService}, and external calls happen in between.
 * A temporary failure doesn't block the worker: the job is put back in the queue for later.
 */
@Component
public class RunExecutor {

	private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

	private sealed interface Outcome {
	}

	private record Succeeded() implements Outcome {
	}

	private record Failed(String error) implements Outcome {
	}

	private record RetryLater(Instant at) implements Outcome {
	}

	private record LeaseLost() implements Outcome {
	}

	private final RunStateService state;
	private final ActionExecutor actionExecutor;
	private final JobQueue jobQueue;
	private final Backoff backoff;
	private final ExecutionProperties properties;
	private final Duration lease;
	private final Clock clock;

	public RunExecutor(RunStateService state, ActionExecutor actionExecutor, JobQueue jobQueue, Backoff backoff,
			ExecutionProperties properties, @Value("${ipaas.worker.lease}") Duration lease, Clock clock) {
		this.state = state;
		this.actionExecutor = actionExecutor;
		this.jobQueue = jobQueue;
		this.backoff = backoff;
		this.properties = properties;
		this.lease = lease;
		this.clock = clock;
	}

	public void execute(ClaimedJob job) {
		Optional<RunPlan> plan = state.start(job.runId());
		if (plan.isEmpty()) {
			jobQueue.delete(job);
			return;
		}
		switch (runSteps(plan.get(), job)) {
			case Succeeded() -> {
				state.finishRun(job.runId(), RunStatus.SUCCEEDED, null);
				jobQueue.delete(job);
				log.info("Run {} succeeded", job.runId());
			}
			case Failed(String error) -> {
				state.finishRun(job.runId(), RunStatus.FAILED, error);
				jobQueue.delete(job);
				log.info("Run {} failed: {}", job.runId(), error);
			}
			case RetryLater(Instant at) -> {
				jobQueue.reschedule(job, at);
				log.info("Run {} will retry at {}", job.runId(), at);
			}
			case LeaseLost() -> log.warn("Lost the lease on run {}; another worker took over", job.runId());
		}
	}

	private Outcome runSteps(RunPlan plan, ClaimedJob job) {
		Map<String, Object> stepsContext = new LinkedHashMap<>();
		for (RunPlan.PlannedStep planned : plan.steps()) {
			StepDefinition step = planned.definition();
			if (planned.status() == StepStatus.SUCCEEDED) {
				stepsContext.put(step.key(), Map.of("output", planned.output()));
				continue;
			}
			if (!jobQueue.extendLease(job, lease)) {
				return new LeaseLost();
			}
			Outcome outcome = runStep(plan, planned, stepsContext);
			if (outcome != null) {
				return outcome;
			}
		}
		return new Succeeded();
	}

	/** Returns null when the step succeeded and the run can go on. */
	private Outcome runStep(RunPlan plan, RunPlan.PlannedStep planned, Map<String, Object> stepsContext) {
		StepDefinition step = planned.definition();
		int maxAttempts = step.maxAttempts() != null ? step.maxAttempts() : properties.maxAttempts();
		if (planned.status() == StepStatus.RUNNING && planned.attempt() >= maxAttempts) {
			return failStep(planned, "The worker stopped during this step " + planned.attempt() + " times; giving up");
		}
		int attempt = planned.attempt() + 1;
		try {
			Map<String, Object> input = TemplateResolver.resolve(step.inputs(),
					Map.of("trigger", plan.trigger(), "steps", stepsContext));
			state.startStep(planned.stepRunId(), input, attempt);

			ActionResult result = actionExecutor.run(step.connector(), step.action(), input, timeout(step)).result();
			if (result.success()) {
				state.finishStep(planned.stepRunId(), StepStatus.SUCCEEDED, result.output(), null);
				stepsContext.put(step.key(), Map.of("output", result.output()));
				return null;
			}
			if (result.retryable() && attempt < maxAttempts) {
				Instant retryAt = clock.instant().plus(backoff.delayAfter(attempt));
				state.waitForRetry(planned.stepRunId(), result.output(), result.error(), retryAt);
				return new RetryLater(retryAt);
			}
			state.finishStep(planned.stepRunId(), StepStatus.FAILED, result.output(), result.error());
			String suffix = result.retryable() ? " (after " + attempt + " attempts)" : "";
			return new Failed("Step '" + step.key() + "' failed: " + result.error() + suffix);
		} catch (TemplateException e) {
			return failStep(planned, e.getMessage());
		} catch (InvalidInputException e) {
			return failStep(planned, "Invalid input: " + e.fieldErrors());
		} catch (RuntimeException e) {
			log.error("Step {} of run {} crashed", step.key(), plan.runId(), e);
			return failStep(planned, "Unexpected error: " + e.getClass().getSimpleName());
		}
	}

	private Duration timeout(StepDefinition step) {
		return step.timeoutSeconds() != null ? Duration.ofSeconds(step.timeoutSeconds()) : properties.stepTimeout();
	}

	private Failed failStep(RunPlan.PlannedStep planned, String error) {
		state.finishStep(planned.stepRunId(), StepStatus.FAILED, null, error);
		return new Failed("Step '" + planned.definition().key() + "' failed: " + error);
	}
}
