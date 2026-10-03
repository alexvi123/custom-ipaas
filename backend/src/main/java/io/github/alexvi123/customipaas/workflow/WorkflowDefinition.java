package io.github.alexvi123.customipaas.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkflowDefinition(String triggerType, List<StepDefinition> steps) {

	public WorkflowDefinition {
		steps = List.copyOf(steps);
	}

	public Map<String, Object> toMap() {
		List<Map<String, Object>> stepMaps = steps.stream().map(step -> {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("key", step.key());
			map.put("connector", step.connector());
			map.put("action", step.action());
			map.put("inputs", step.inputs());
			return map;
		}).toList();
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("trigger", Map.of("type", triggerType));
		map.put("steps", stepMaps);
		return map;
	}

	/** Reads a definition that was validated before it was stored. */
	@SuppressWarnings("unchecked")
	public static WorkflowDefinition fromStored(Map<String, Object> stored) {
		Map<String, Object> trigger = (Map<String, Object>) stored.get("trigger");
		List<Map<String, Object>> steps = (List<Map<String, Object>>) stored.get("steps");
		return new WorkflowDefinition((String) trigger.get("type"), steps.stream()
				.map(step -> new StepDefinition((String) step.get("key"), (String) step.get("connector"),
						(String) step.get("action"), (Map<String, Object>) step.get("inputs")))
				.toList());
	}
}
