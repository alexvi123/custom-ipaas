package io.github.alexvi123.customipaas.workflow;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionNotFoundException;
import io.github.alexvi123.customipaas.connector.ConnectorNotFoundException;
import io.github.alexvi123.customipaas.connector.ConnectorRegistry;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.InputValidator;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import io.github.alexvi123.customipaas.execution.TemplateResolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks a workflow definition before it is saved and turns it into a {@link WorkflowDefinition}.
 * Reports every problem at once, keyed by JSON path.
 */
@Component
public class WorkflowDefinitionValidator {

	private static final Pattern STEP_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
	private static final Set<String> TOP_LEVEL_KEYS = Set.of("trigger", "steps");
	private static final Set<String> STEP_KEYS = Set.of("key", "connector", "action", "inputs");

	private final ConnectorRegistry registry;
	private final InputValidator inputValidator;

	public WorkflowDefinitionValidator(ConnectorRegistry registry, InputValidator inputValidator) {
		this.registry = registry;
		this.inputValidator = inputValidator;
	}

	public WorkflowDefinition validate(Map<String, Object> raw) {
		Map<String, String> errors = new LinkedHashMap<>();
		raw.keySet().stream().filter(key -> !TOP_LEVEL_KEYS.contains(key)).forEach(key -> errors.put(key, "Unknown property"));

		if (!(raw.get("trigger") instanceof Map<?, ?> trigger) || !"webhook".equals(trigger.get("type"))) {
			errors.put("trigger.type", "Must be \"webhook\"");
		}

		List<StepDefinition> steps = List.of();
		if (raw.get("steps") instanceof List<?> rawSteps && !rawSteps.isEmpty()) {
			steps = validateSteps(rawSteps, errors);
		} else {
			errors.put("steps", "Must be a non-empty list of steps");
		}

		if (!errors.isEmpty()) {
			throw new InvalidWorkflowException(errors);
		}
		return new WorkflowDefinition("webhook", steps);
	}

	private List<StepDefinition> validateSteps(List<?> rawSteps, Map<String, String> errors) {
		Set<String> earlierKeys = new HashSet<>();
		List<StepDefinition> steps = new ArrayList<>();
		for (int i = 0; i < rawSteps.size(); i++) {
			String path = "steps[" + i + "]";
			if (!(rawSteps.get(i) instanceof Map<?, ?> rawStep)) {
				errors.put(path, "Must be an object");
				continue;
			}
			rawStep.keySet().stream().map(Object::toString).filter(key -> !STEP_KEYS.contains(key))
					.forEach(key -> errors.put(path + "." + key, "Unknown property"));

			String key = text(rawStep.get("key"));
			if (key == null || !STEP_KEY.matcher(key).matches()) {
				errors.put(path + ".key", "Required: letters, digits and _ (starting with a letter)");
			} else if (earlierKeys.contains(key)) {
				errors.put(path + ".key", "Duplicate step key '" + key + "'");
			}

			Map<String, Object> inputs = inputs(rawStep.get("inputs"), path, errors);
			String connector = text(rawStep.get("connector"));
			String action = text(rawStep.get("action"));
			ActionDefinition actionDefinition = findAction(connector, action, path, errors);
			if (actionDefinition != null) {
				validateInputs(actionDefinition, inputs, path, earlierKeys, errors);
			}

			if (key != null) {
				steps.add(new StepDefinition(key, connector, action, inputs));
				earlierKeys.add(key);
			}
		}
		return steps;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> inputs(Object rawInputs, String path, Map<String, String> errors) {
		if (rawInputs == null) {
			return Map.of();
		}
		if (rawInputs instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		errors.put(path + ".inputs", "Must be an object");
		return Map.of();
	}

	private ActionDefinition findAction(String connector, String action, String path, Map<String, String> errors) {
		if (connector == null) {
			errors.put(path + ".connector", "Required");
			return null;
		}
		if (action == null) {
			errors.put(path + ".action", "Required");
			return null;
		}
		try {
			return registry.action(connector, action);
		} catch (ConnectorNotFoundException e) {
			errors.put(path + ".connector", "Unknown connector '" + connector + "'");
		} catch (ActionNotFoundException e) {
			errors.put(path + ".action", "Unknown action '" + action + "' for connector '" + connector + "'");
		}
		return null;
	}

	private void validateInputs(ActionDefinition action, Map<String, Object> inputs, String path,
			Set<String> earlierKeys, Map<String, String> errors) {
		inputs.keySet().stream().filter(key -> action.field(key).isEmpty())
				.forEach(key -> errors.put(path + ".inputs." + key, "Unknown field"));

		for (FieldDefinition field : action.fields()) {
			String fieldPath = path + ".inputs." + field.key();
			Object value = inputs.get(field.key());
			if (value == null || (value instanceof String text && text.isBlank())) {
				if (field.required() && field.defaultValue() == null) {
					errors.put(fieldPath, "Required");
				}
				continue;
			}
			if (TemplateResolver.containsPlaceholder(value)) {
				checkReferences(value, fieldPath, earlierKeys, errors);
				continue;
			}
			try {
				inputValidator.validate(List.of(field), Map.of(field.key(), value));
			} catch (InvalidInputException e) {
				errors.put(fieldPath, e.fieldErrors().get(field.key()));
			}
		}
	}

	private static void checkReferences(Object value, String fieldPath, Set<String> earlierKeys, Map<String, String> errors) {
		for (String reference : TemplateResolver.references(value)) {
			String[] segments = reference.split("\\.");
			String problem = switch (segments[0]) {
				case "trigger" -> null;
				case "steps" -> {
					if (segments.length < 3 || !"output".equals(segments[2])) {
						yield "Use {{steps.<key>.output...}}";
					}
					yield earlierKeys.contains(segments[1]) ? null
							: "{{" + reference + "}} refers to step '" + segments[1] + "', which doesn't run before this step";
				}
				default -> "{{" + reference + "}} must start with 'trigger' or 'steps'";
			};
			if (problem != null) {
				errors.put(fieldPath, problem);
			}
		}
	}

	private static String text(Object value) {
		return value instanceof String text && !text.isBlank() ? text : null;
	}
}
