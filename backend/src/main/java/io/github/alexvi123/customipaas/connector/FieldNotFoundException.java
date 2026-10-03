package io.github.alexvi123.customipaas.connector;

public class FieldNotFoundException extends RuntimeException {

	public FieldNotFoundException(String connectorKey, String actionKey, String fieldKey) {
		super("Action '" + connectorKey + "." + actionKey + "' has no field '" + fieldKey + "' with dynamic options");
	}
}
