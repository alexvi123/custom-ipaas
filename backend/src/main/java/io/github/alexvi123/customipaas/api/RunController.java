package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.api.RunDtos.RunDetails;
import io.github.alexvi123.customipaas.api.RunDtos.RunSummary;
import io.github.alexvi123.customipaas.execution.RunQueryService;
import io.github.alexvi123.customipaas.execution.RunRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final RunQueryService runQueryService;
	private final RunRetryService runRetryService;

	public RunController(RunQueryService runQueryService, RunRetryService runRetryService) {
		this.runQueryService = runQueryService;
		this.runRetryService = runRetryService;
	}

	@GetMapping
	@Operation(summary = "A workflow's runs, newest first (pass the last createdAt as 'before' for the next page)")
	public List<RunSummary> list(@RequestParam UUID workflowId, @RequestParam(required = false) Instant before,
			@RequestParam(defaultValue = "20") int limit) {
		return runQueryService.list(workflowId, before, limit).stream().map(RunSummary::from).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "A run with the input, output and outcome of every step")
	@ApiResponse(responseCode = "200", description = "The run")
	@ApiResponse(responseCode = "404", description = "Unknown run",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public RunDetails get(@PathVariable UUID id) {
		return RunDetails.from(runQueryService.get(id));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Retry a failed run: it continues from the failed step, with the workflow version it started with")
	@ApiResponse(responseCode = "202", description = "The run is queued again")
	@ApiResponse(responseCode = "404", description = "Unknown run",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "The run has not failed",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public RunSummary retry(@PathVariable UUID id) {
		return RunSummary.from(runRetryService.retry(id));
	}
}
