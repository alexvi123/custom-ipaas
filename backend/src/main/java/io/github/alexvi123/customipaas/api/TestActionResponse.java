package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.connector.ActionRun;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** The test ran: {@code success} says whether the external app accepted it. */
public record TestActionResponse(
		@Schema(requiredMode = REQUIRED) boolean success,
		@Schema(requiredMode = REQUIRED) Map<String, Object> output,
		String error,
		@Schema(requiredMode = REQUIRED) long durationMs) {

	static TestActionResponse from(ActionRun run) {
		return new TestActionResponse(run.result().success(), run.result().output(), run.result().error(),
				run.durationMs());
	}
}
