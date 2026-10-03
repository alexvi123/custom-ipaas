package io.github.alexvi123.customipaas.connector;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

public record FieldDefinition(
		@Schema(requiredMode = REQUIRED) String key,
		@Schema(requiredMode = REQUIRED) String label,
		@Schema(requiredMode = REQUIRED) FieldType type,
		@Schema(requiredMode = REQUIRED) boolean required,
		String help,
		String placeholder,
		String defaultValue,
		@Schema(requiredMode = REQUIRED) List<Option> options,
		@Schema(requiredMode = REQUIRED) boolean dynamicOptions,
		@Schema(requiredMode = REQUIRED) boolean allowCustomValue,
		@Schema(requiredMode = REQUIRED) List<String> dependsOn) {

	public FieldDefinition {
		options = List.copyOf(options);
		dependsOn = List.copyOf(dependsOn);
	}

	private static FieldDefinition of(String key, String label, FieldType type) {
		return new FieldDefinition(key, label, type, false, null, null, null, List.of(), false, false, List.of());
	}

	public static FieldDefinition string(String key, String label) {
		return of(key, label, FieldType.STRING);
	}

	public static FieldDefinition text(String key, String label) {
		return of(key, label, FieldType.TEXT);
	}

	public static FieldDefinition number(String key, String label) {
		return of(key, label, FieldType.NUMBER);
	}

	public static FieldDefinition bool(String key, String label) {
		return of(key, label, FieldType.BOOLEAN);
	}

	public static FieldDefinition json(String key, String label) {
		return of(key, label, FieldType.JSON);
	}

	public static FieldDefinition select(String key, String label, Option... options) {
		return of(key, label, FieldType.SELECT).withOptions(List.of(options));
	}

	public FieldDefinition asRequired() {
		return new FieldDefinition(key, label, type, true, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, dependsOn);
	}

	public FieldDefinition withHelp(String help) {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, dependsOn);
	}

	public FieldDefinition withPlaceholder(String placeholder) {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, dependsOn);
	}

	public FieldDefinition withDefault(String defaultValue) {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, dependsOn);
	}

	public FieldDefinition withOptions(List<Option> options) {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, dependsOn);
	}

	/** Options are loaded live from the connector */
	public FieldDefinition withDynamicOptions() {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, true,
				allowCustomValue, dependsOn);
	}

	/** The user may type a value that is not in the options. */
	public FieldDefinition withCustomValue() {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				true, dependsOn);
	}

	/** Dynamic options depend on the values of these other fields. */
	public FieldDefinition withDependsOn(String... fieldKeys) {
		return new FieldDefinition(key, label, type, required, help, placeholder, defaultValue, options, dynamicOptions,
				allowCustomValue, Arrays.asList(fieldKeys));
	}
}
