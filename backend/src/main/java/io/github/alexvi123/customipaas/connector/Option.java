package io.github.alexvi123.customipaas.connector;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** One choice in a select field: the value sent to the connector and the label shown to the user. */
public record Option(
		@Schema(requiredMode = REQUIRED) String value,
		@Schema(requiredMode = REQUIRED) String label) {
}
