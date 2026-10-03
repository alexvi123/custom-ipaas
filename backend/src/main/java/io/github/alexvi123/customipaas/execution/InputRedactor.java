package io.github.alexvi123.customipaas.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Masks secret-looking values (tokens, passwords, Authorization headers...) before step input is stored. */
public final class InputRedactor {

	private static final Pattern SENSITIVE_KEY =
			Pattern.compile(".*(authorization|token|password|secret|api[-_]?key|cookie).*", Pattern.CASE_INSENSITIVE);
	static final String MASK = "***";

	private InputRedactor() {
	}

	public static Map<String, Object> redact(Map<String, Object> values) {
		Map<String, Object> redacted = new LinkedHashMap<>();
		values.forEach((key, value) -> redacted.put(key, SENSITIVE_KEY.matcher(key).matches() ? MASK : redactValue(value)));
		return redacted;
	}

	@SuppressWarnings("unchecked")
	private static Object redactValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return redact((Map<String, Object>) map);
		}
		if (value instanceof List<?> list) {
			return list.stream().map(InputRedactor::redactValue).toList();
		}
		return value;
	}
}
