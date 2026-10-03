package io.github.alexvi123.customipaas.connector;

import java.util.Map;
import java.util.Optional;

/** Validated input values for one action run, keyed by field key. */
public record ActionInput(Map<String, Object> values) {

	public ActionInput {
		values = Map.copyOf(values);
	}

	public Optional<String> string(String key) {
		return Optional.ofNullable(values.get(key)).map(Object::toString);
	}

	public String requireString(String key) {
		return string(key).orElseThrow(() -> new IllegalStateException("Missing input '" + key + "'"));
	}

	public Optional<Object> raw(String key) {
		return Optional.ofNullable(values.get(key));
	}
}
