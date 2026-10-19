package io.github.alexvi123.customipaas.execution;

import io.github.alexvi123.customipaas.workflow.StepDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RunPlan(UUID runId, UUID workflowId, Map<String, Object> trigger, List<PlannedStep> steps) {

	/** A step plus what happened to it so far, so a resumed run can skip what already succeeded. */
	public record PlannedStep(UUID stepRunId, StepDefinition definition, StepStatus status, int attempt,
			Map<String, Object> output) {
	}
}
