package io.github.alexvi123.customipaas.execution;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

	public RunNotFoundException(UUID id) {
		super("Unknown run '" + id + "'");
	}
}
