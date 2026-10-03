package io.github.alexvi123.customipaas.workflow;

import java.util.Map;

public class InvalidWorkflowException extends RuntimeException {

	private final Map<String, String> errors;

	public InvalidWorkflowException(Map<String, String> errors) {
		super("Invalid workflow: " + errors);
		this.errors = Map.copyOf(errors);
	}

	public Map<String, String> errors() {
		return errors;
	}
}
