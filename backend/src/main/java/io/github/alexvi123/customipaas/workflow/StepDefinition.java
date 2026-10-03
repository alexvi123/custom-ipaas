package io.github.alexvi123.customipaas.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record StepDefinition(String key, String connector, String action, Map<String, Object> inputs) {

	public StepDefinition {
		inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
	}
}
