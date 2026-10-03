package io.github.alexvi123.customipaas.connector;

public class ConnectorNotFoundException extends RuntimeException {

	public ConnectorNotFoundException(String connectorKey) {
		super("Unknown connector '" + connectorKey + "'");
	}
}
