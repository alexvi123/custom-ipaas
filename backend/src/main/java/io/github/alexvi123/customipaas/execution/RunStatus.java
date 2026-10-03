package io.github.alexvi123.customipaas.execution;

public enum RunStatus {
	PENDING,
	RUNNING,
	SUCCEEDED,
	FAILED;

	public boolean isFinished() {
		return this == SUCCEEDED || this == FAILED;
	}
}
