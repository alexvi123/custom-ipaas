package io.github.alexvi123.customipaas.connector;

public class ConnectorException extends RuntimeException {

	private final boolean retryable;

	public ConnectorException(String message) {
		this(message, false);
	}

	public ConnectorException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	public ConnectorException(String message, Throwable cause) {
		super(message, cause);
		this.retryable = false;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
