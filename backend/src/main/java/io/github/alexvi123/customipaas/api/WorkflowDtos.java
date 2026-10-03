package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.workflow.Workflow;
import io.github.alexvi123.customipaas.workflow.WorkflowWithVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the workflow API. */
final class WorkflowDtos {

	private WorkflowDtos() {
	}

	record SaveWorkflowRequest(
			@Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 200) String name,
			@Schema(requiredMode = REQUIRED) @NotNull Map<String, Object> definition) {
	}

	record WorkflowSummary(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) boolean active,
			@Schema(requiredMode = REQUIRED) int version,
			@Schema(requiredMode = REQUIRED) Instant updatedAt) {

		static WorkflowSummary from(Workflow workflow) {
			return new WorkflowSummary(workflow.getId(), workflow.getName(), workflow.isActive(),
					workflow.getCurrentVersion(), workflow.getUpdatedAt());
		}
	}

	record WorkflowDetails(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(requiredMode = REQUIRED) String name,
			@Schema(requiredMode = REQUIRED) boolean active,
			@Schema(requiredMode = REQUIRED) int version,
			@Schema(requiredMode = REQUIRED) Map<String, Object> definition,
			@Schema(requiredMode = REQUIRED) String webhookPath,
			@Schema(requiredMode = REQUIRED) Instant createdAt,
			@Schema(requiredMode = REQUIRED) Instant updatedAt) {

		static WorkflowDetails from(WorkflowWithVersion loaded) {
			Workflow workflow = loaded.workflow();
			return new WorkflowDetails(workflow.getId(), workflow.getName(), workflow.isActive(),
					workflow.getCurrentVersion(), loaded.version().getDefinition(),
					"/hooks/" + workflow.getWebhookToken(), workflow.getCreatedAt(), workflow.getUpdatedAt());
		}
	}
}
