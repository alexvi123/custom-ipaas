package io.github.alexvi123.customipaas.connector;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;

/** Something a connector can do, plus the input fields it needs. */
public record ActionDefinition(
		@Schema(requiredMode = REQUIRED) String key,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(requiredMode = REQUIRED) List<FieldDefinition> fields) {

	public ActionDefinition {
		fields = List.copyOf(fields);
	}

	public Optional<FieldDefinition> field(String fieldKey) {
		return fields.stream().filter(f -> f.key().equals(fieldKey)).findFirst();
	}
}
