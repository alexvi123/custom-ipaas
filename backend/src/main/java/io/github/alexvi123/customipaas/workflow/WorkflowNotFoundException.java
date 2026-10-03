package io.github.alexvi123.customipaas.workflow;

import java.util.UUID;

public class WorkflowNotFoundException extends RuntimeException {

	public WorkflowNotFoundException(UUID id) {
		super("Unknown workflow '" + id + "'");
	}
}
