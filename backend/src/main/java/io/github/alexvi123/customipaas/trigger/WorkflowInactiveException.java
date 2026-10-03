package io.github.alexvi123.customipaas.trigger;

public class WorkflowInactiveException extends RuntimeException {

	public WorkflowInactiveException(String workflowName) {
		super("Workflow '" + workflowName + "' is not active. Activate it to accept webhooks.");
	}
}
