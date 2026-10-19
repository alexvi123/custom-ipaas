package io.github.alexvi123.customipaas.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of running an action. Bugs are thrown as exceptions. A retryable failure is temporary (network, timeout,
 * 5xx) and worth trying again later; any other failure won't fix itself.
 */
public record ActionResult(boolean success, Map<String, Object> output, String error, boolean retryable) {

	public ActionResult {
		output = Collections.unmodifiableMap(new LinkedHashMap<>(output));
	}

	public static ActionResult success(Map<String, Object> output) {
		return new ActionResult(true, output, null, false);
	}

	public static ActionResult failure(String error) {
		return new ActionResult(false, Map.of(), error, false);
	}

	public static ActionResult failure(String error, Map<String, Object> output) {
		return new ActionResult(false, output, error, false);
	}

	public static ActionResult retryableFailure(String error) {
		return new ActionResult(false, Map.of(), error, true);
	}

	public static ActionResult retryableFailure(String error, Map<String, Object> output) {
		return new ActionResult(false, output, error, true);
	}

	static ActionResult from(ConnectorException e) {
		return e.isRetryable() ? retryableFailure(e.getMessage()) : failure(e.getMessage());
	}
}
