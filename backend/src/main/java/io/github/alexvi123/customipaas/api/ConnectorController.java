package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.connector.ConnectorService;
import io.github.alexvi123.customipaas.connector.Option;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final ConnectorService connectorService;

	public ConnectorController(ConnectorService connectorService) {
		this.connectorService = connectorService;
	}

	@GetMapping
	@Operation(summary = "List all connectors")
	public List<ConnectorSummary> list() {
		return connectorService.list().stream().map(ConnectorSummary::from).toList();
	}

	@GetMapping("/{key}")
	@Operation(summary = "Get a connector's actions and their field definitions")
	@ApiResponse(responseCode = "200", description = "The connector")
	@ApiResponse(responseCode = "404", description = "Unknown connector",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public ConnectorDetails details(@PathVariable String key) {
		return ConnectorDetails.from(connectorService.get(key));
	}

	@PostMapping("/{key}/actions/{action}/fields/{field}/options")
	@Operation(summary = "Load live options for a select field")
	@ApiResponse(responseCode = "200", description = "The options")
	@ApiResponse(responseCode = "404", description = "Unknown connector, action or dynamic field",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "502", description = "The external app or the connector's configuration failed",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public List<Option> options(@PathVariable String key, @PathVariable String action, @PathVariable String field,
			@RequestBody(required = false) OptionsRequest request) {
		OptionsRequest body = request == null ? new OptionsRequest(null) : request;
		return connectorService.options(key, action, field, body.values());
	}

	@PostMapping("/{key}/actions/{action}/test")
	@Operation(summary = "Run an action once (the \"Test step\" button)")
	@ApiResponse(responseCode = "200", description = "The test ran; success says whether the external app accepted it")
	@ApiResponse(responseCode = "400", description = "Input doesn't match the action's fields",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Unknown connector or action",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public TestActionResponse test(@PathVariable String key, @PathVariable String action,
			@Valid @RequestBody TestActionRequest request) {
		return TestActionResponse.from(connectorService.test(key, action, request.values()));
	}
}
