package io.github.alexvi123.customipaas.connector;

public class ConnectorException extends RuntimeException {

	public ConnectorException(String message) {
		super(message);
	}

	public ConnectorException(String message, Throwable cause) {
		super(message, cause);
	}
}
