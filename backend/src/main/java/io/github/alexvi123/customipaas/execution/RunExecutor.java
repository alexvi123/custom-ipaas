package io.github.alexvi123.customipaas.execution;

import io.github.alexvi123.customipaas.connector.ActionExecutor;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.ActionRun;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import io.github.alexvi123.customipaas.workflow.StepDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the steps of one claimed job in order. No transaction is open here: each status change is its own
 * short transaction in {@link RunStateService}, and external calls happen in between.
 */
@Component
public class RunExecutor {

	private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

	private final RunStateService state;
	private final ActionExecutor actionExecutor;
	private final JobQueue jobQueue;

	public RunExecutor(RunStateService state, ActionExecutor actionExecutor, JobQueue jobQueue) {
		this.state = state;
		this.actionExecutor = actionExecutor;
		this.jobQueue = jobQueue;
	}

	public void execute(ClaimedJob job) {
		Optional<RunPlan> plan = state.start(job.runId());
		if (plan.isPresent()) {
			long start = System.nanoTime();
			String error = runSteps(plan.get());
			state.finishRun(plan.get().runId(), error == null ? RunStatus.SUCCEEDED : RunStatus.FAILED, error);
			log.info("Run {} finished: {} in {} ms", plan.get().runId(), error == null ? "SUCCEEDED" : "FAILED",
					(System.nanoTime() - start) / 1_000_000);
		}
		jobQueue.delete(job.id());
	}

	/** Returns null when every step succeeded, otherwise the run's error message. */
	private String runSteps(RunPlan plan) {
		Map<String, Object> stepsContext = new LinkedHashMap<>();
		for (RunPlan.PlannedStep planned : plan.steps()) {
			StepDefinition step = planned.definition();
			Map<String, Object> context = Map.of("trigger", plan.trigger(), "steps", stepsContext);
			try {
				Map<String, Object> input = TemplateResolver.resolve(step.inputs(), context);
				state.startStep(planned.stepRunId(), input);

				ActionRun run = actionExecutor.run(step.connector(), step.action(), input);
				ActionResult result = run.result();
				state.finishStep(planned.stepRunId(), result.success() ? StepStatus.SUCCEEDED : StepStatus.FAILED,
						result.output(), result.error());
				if (!result.success()) {
					return "Step '" + step.key() + "' failed: " + result.error();
				}
				stepsContext.put(step.key(), Map.of("output", result.output()));
			} catch (TemplateException e) {
				return failStep(planned, step, e.getMessage());
			} catch (InvalidInputException e) {
				return failStep(planned, step, "Invalid input: " + e.fieldErrors());
			} catch (RuntimeException e) {
				log.error("Step {} of run {} crashed", step.key(), plan.runId(), e);
				return failStep(planned, step, "Unexpected error: " + e.getClass().getSimpleName());
			}
		}
		return null;
	}

	private String failStep(RunPlan.PlannedStep planned, StepDefinition step, String error) {
		state.finishStep(planned.stepRunId(), StepStatus.FAILED, null, error);
		return "Step '" + step.key() + "' failed: " + error;
	}
}
