package io.github.alexvi123.customipaas.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of running an action. Bugs are thrown as exceptions.
 */
public record ActionResult(boolean success, Map<String, Object> output, String error) {

	public ActionResult {
		output = Collections.unmodifiableMap(new LinkedHashMap<>(output));
	}

	public static ActionResult success(Map<String, Object> output) {
		return new ActionResult(true, output, null);
	}

	public static ActionResult failure(String error) {
		return new ActionResult(false, Map.of(), error);
	}

	public static ActionResult failure(String error, Map<String, Object> output) {
		return new ActionResult(false, output, error);
	}
}
