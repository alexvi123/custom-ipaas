package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.connector.Connector;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectorSummary(
		@Schema(requiredMode = REQUIRED) String key,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description) {

	static ConnectorSummary from(Connector connector) {
		return new ConnectorSummary(connector.info().key(), connector.info().name(), connector.info().description());
	}
}
