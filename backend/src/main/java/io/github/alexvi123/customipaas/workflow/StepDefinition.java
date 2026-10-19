package io.github.alexvi123.customipaas.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One step of a workflow. {@code maxAttempts} and {@code timeoutSeconds} are null when the step uses the defaults. */
public record StepDefinition(String key, String connector, String action, Map<String, Object> inputs,
		Integer maxAttempts, Integer timeoutSeconds) {

	public StepDefinition {
		inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
	}

	public StepDefinition(String key, String connector, String action, Map<String, Object> inputs) {
		this(key, connector, action, inputs, null, null);
	}
}
