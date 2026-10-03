package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.Connector;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A connector with its actions and their field definitions: everything the frontend needs to render forms. */
public record ConnectorDetails(
		@Schema(requiredMode = REQUIRED) String key,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(requiredMode = REQUIRED) List<ActionDefinition> actions) {

	static ConnectorDetails from(Connector connector) {
		return new ConnectorDetails(connector.info().key(), connector.info().name(), connector.info().description(),
				connector.actions());
	}
}
