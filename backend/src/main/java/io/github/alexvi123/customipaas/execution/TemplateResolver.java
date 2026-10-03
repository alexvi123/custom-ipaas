package io.github.alexvi123.customipaas.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

public final class TemplateResolver {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
	private static final List<String> ROOTS = List.of("trigger", "steps");
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private TemplateResolver() {
	}

	public static Map<String, Object> resolve(Map<String, Object> inputs, Map<String, Object> context) {
		Map<String, Object> resolved = new LinkedHashMap<>();
		inputs.forEach((key, value) -> resolved.put(key, resolveValue(value, context)));
		return resolved;
	}

	/** Every placeholder path in a value (strings, nested objects and arrays), e.g. "trigger.body.name". */
	public static List<String> references(Object value) {
		List<String> paths = new ArrayList<>();
		collectReferences(value, paths);
		return paths;
	}

	public static boolean containsPlaceholder(Object value) {
		return !references(value).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private static Object resolveValue(Object value, Map<String, Object> context) {
		if (value instanceof String text) {
			Matcher whole = PLACEHOLDER.matcher(text);
			if (whole.matches()) {
				return lookup(whole.group(1), context);
			}
			Matcher matcher = PLACEHOLDER.matcher(text);
			StringBuilder result = new StringBuilder();
			while (matcher.find()) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(asText(lookup(matcher.group(1), context))));
			}
			matcher.appendTail(result);
			return result.toString();
		}
		if (value instanceof Map<?, ?> map) {
			return resolve((Map<String, Object>) map, context);
		}
		if (value instanceof List<?> list) {
			return list.stream().map(item -> resolveValue(item, context)).toList();
		}
		return value;
	}

	private static Object lookup(String path, Map<String, Object> context) {
		String[] segments = path.split("\\.");
		if (!ROOTS.contains(segments[0])) {
			throw new TemplateException("Template {{" + path + "}} must start with 'trigger' or 'steps'");
		}
		Object current = context;
		for (String segment : segments) {
			current = child(current, segment);
			if (current == null) {
				throw new TemplateException("Template {{" + path + "}} has no value");
			}
		}
		return current;
	}

	private static Object child(Object parent, String segment) {
		if (parent instanceof Map<?, ?> map) {
			return map.get(segment);
		}
		if (parent instanceof List<?> list && segment.matches("\\d+")) {
			int index = Integer.parseInt(segment);
			return index < list.size() ? list.get(index) : null;
		}
		return null;
	}

	private static String asText(Object value) {
		return value instanceof Map || value instanceof List ? JSON.writeValueAsString(value) : String.valueOf(value);
	}

	private static void collectReferences(Object value, List<String> paths) {
		if (value instanceof String text) {
			Matcher matcher = PLACEHOLDER.matcher(text);
			while (matcher.find()) {
				paths.add(matcher.group(1));
			}
		} else if (value instanceof Map<?, ?> map) {
			map.values().forEach(item -> collectReferences(item, paths));
		} else if (value instanceof List<?> list) {
			list.forEach(item -> collectReferences(item, paths));
		}
	}
}
