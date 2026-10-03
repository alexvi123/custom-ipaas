package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.api.WorkflowDtos.SaveWorkflowRequest;
import io.github.alexvi123.customipaas.api.WorkflowDtos.WorkflowDetails;
import io.github.alexvi123.customipaas.api.WorkflowDtos.WorkflowSummary;
import io.github.alexvi123.customipaas.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final WorkflowService workflowService;

	public WorkflowController(WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@GetMapping
	@Operation(summary = "List workflows, newest first")
	public List<WorkflowSummary> list() {
		return workflowService.list().stream().map(WorkflowSummary::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a workflow (version 1, inactive)")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "The definition has problems (errors per JSON path)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public WorkflowDetails create(@Valid @RequestBody SaveWorkflowRequest request) {
		return WorkflowDetails.from(workflowService.create(request.name(), request.definition()));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a workflow with its current definition and webhook path")
	@ApiResponse(responseCode = "200", description = "The workflow")
	@ApiResponse(responseCode = "404", description = "Unknown workflow",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public WorkflowDetails get(@PathVariable UUID id) {
		return WorkflowDetails.from(workflowService.get(id));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Save changes as a new version")
	@ApiResponse(responseCode = "200", description = "Saved")
	@ApiResponse(responseCode = "400", description = "The definition has problems (errors per JSON path)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Unknown workflow",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public WorkflowDetails update(@PathVariable UUID id, @Valid @RequestBody SaveWorkflowRequest request) {
		return WorkflowDetails.from(workflowService.update(id, request.name(), request.definition()));
	}

	@PostMapping("/{id}/activate")
	@Operation(summary = "Start accepting webhooks")
	public WorkflowDetails activate(@PathVariable UUID id) {
		return WorkflowDetails.from(workflowService.setActive(id, true));
	}

	@PostMapping("/{id}/deactivate")
	@Operation(summary = "Stop accepting webhooks")
	public WorkflowDetails deactivate(@PathVariable UUID id) {
		return WorkflowDetails.from(workflowService.setActive(id, false));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a workflow and its runs")
	public void delete(@PathVariable UUID id) {
		workflowService.delete(id);
	}
}
