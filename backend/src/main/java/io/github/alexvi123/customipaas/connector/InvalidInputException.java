package io.github.alexvi123.customipaas.connector;

import java.util.Map;

/** Input values don't match the action's field definitions. Carries one message per field. */
public class InvalidInputException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public InvalidInputException(Map<String, String> fieldErrors) {
		super("Invalid input: " + fieldErrors);
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> fieldErrors() {
		return fieldErrors;
	}
}
