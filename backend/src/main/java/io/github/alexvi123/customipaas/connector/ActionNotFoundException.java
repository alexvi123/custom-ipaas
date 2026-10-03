package io.github.alexvi123.customipaas.connector;

public class ActionNotFoundException extends RuntimeException {

	public ActionNotFoundException(String connectorKey, String actionKey) {
		super("Connector '" + connectorKey + "' has no action '" + actionKey + "'");
	}
}
