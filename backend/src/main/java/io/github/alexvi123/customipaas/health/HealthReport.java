package io.github.alexvi123.customipaas.health;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a health check, returned as JSON by {@code GET /api/health}.
 * The {@code @Schema(requiredMode = REQUIRED)} marks tell the OpenAPI spec both fields are always present,
 * so the generated TypeScript types are non-optional.
 *
 * @param status   overall status: UP only if every dependency is UP
 * @param database status of the PostgreSQL connection
 */
public record HealthReport(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ComponentStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ComponentStatus database) {
}
