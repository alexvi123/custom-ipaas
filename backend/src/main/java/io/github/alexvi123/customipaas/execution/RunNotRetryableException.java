package io.github.alexvi123.customipaas.execution;

public class RunNotRetryableException extends RuntimeException {

	public RunNotRetryableException(RunStatus status) {
		super("Only failed runs can be retried; this run is " + status);
	}
}
