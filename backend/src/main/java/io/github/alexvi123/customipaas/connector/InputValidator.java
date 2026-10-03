package io.github.alexvi123.customipaas.connector;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks input values against an action's field definitions and normalizes them
 * Reports every invalid field at once.
 */
@Component
public class InputValidator {

	private final JsonMapper jsonMapper;

	public InputValidator(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public Map<String, Object> validate(List<FieldDefinition> fields, Map<String, Object> values) {
		Map<String, String> errors = new LinkedHashMap<>();
		Map<String, Object> normalized = new LinkedHashMap<>();

		Set<String> knownKeys = fields.stream().map(FieldDefinition::key).collect(Collectors.toSet());
		values.keySet().stream().filter(key -> !knownKeys.contains(key)).forEach(key -> errors.put(key, "Unknown field"));

		for (FieldDefinition field : fields) {
			Object raw = values.get(field.key());
			if (isBlank(raw)) {
				if (field.defaultValue() == null) {
					if (field.required()) {
						errors.put(field.key(), "Required");
					}
					continue;
				}
				raw = field.defaultValue();
			}
			try {
				normalized.put(field.key(), normalize(field, raw));
			} catch (IllegalArgumentException e) {
				errors.put(field.key(), e.getMessage());
			}
		}

		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
		return normalized;
	}

	private Object normalize(FieldDefinition field, Object raw) {
		return switch (field.type()) {
			case STRING, TEXT -> toText(raw);
			case NUMBER -> toNumber(raw);
			case BOOLEAN -> toBoolean(raw);
			case SELECT -> toSelectValue(field, raw);
			case JSON -> toJson(raw);
		};
	}

	private static boolean isBlank(Object value) {
		return value == null || (value instanceof String text && text.isBlank());
	}

	private static String toText(Object raw) {
		if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
			return raw.toString();
		}
		throw new IllegalArgumentException("Must be text");
	}

	private static BigDecimal toNumber(Object raw) {
		try {
			return new BigDecimal(raw.toString().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Must be a number");
		}
	}

	private static Boolean toBoolean(Object raw) {
		if (raw instanceof Boolean bool) {
			return bool;
		}
		if (raw instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
			return Boolean.valueOf(text);
		}
		throw new IllegalArgumentException("Must be true or false");
	}

	private static String toSelectValue(FieldDefinition field, Object raw) {
		String value = toText(raw);
		boolean restrictedToOptions = !field.options().isEmpty() && !field.allowCustomValue() && !field.dynamicOptions();
		if (restrictedToOptions && field.options().stream().noneMatch(option -> option.value().equals(value))) {
			String allowed = field.options().stream().map(Option::value).collect(Collectors.joining(", "));
			throw new IllegalArgumentException("Must be one of: " + allowed);
		}
		return value;
	}

	private Object toJson(Object raw) {
		if (!(raw instanceof String text)) {
			return raw; // already structured JSON (object, array, number...) in the request body
		}
		try {
			return jsonMapper.readValue(text, Object.class);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Must be valid JSON");
		}
	}
}
