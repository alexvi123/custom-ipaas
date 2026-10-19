package io.github.alexvi123.customipaas.execution;

public enum StepStatus {
	PENDING,
	RUNNING,
	WAITING_RETRY,
	SUCCEEDED,
	FAILED,
	SKIPPED
}
