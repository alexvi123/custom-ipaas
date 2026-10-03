package io.github.alexvi123.customipaas.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.github.alexvi123.customipaas.trigger.InvalidPayloadException;
import io.github.alexvi123.customipaas.trigger.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Public entry point for webhook triggers. Answers immediately; the worker runs the steps later. */
@RestController
public class WebhookController {

	private static final String PROBLEM_JSON = "application/problem+json";
	private static final Set<String> NEVER_STORED_HEADERS = Set.of("authorization", "cookie", "proxy-authorization");

	public record WebhookAccepted(@Schema(requiredMode = REQUIRED) UUID runId) {
	}

	private final WebhookService webhookService;
	private final JsonMapper jsonMapper;
	private final int maxBodyBytes;

	public WebhookController(WebhookService webhookService, JsonMapper jsonMapper,
			@Value("${ipaas.webhooks.max-body-bytes}") int maxBodyBytes) {
		this.webhookService = webhookService;
		this.jsonMapper = jsonMapper;
		this.maxBodyBytes = maxBodyBytes;
	}

	@PostMapping(path = "/hooks/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Webhook trigger: queue a run of the workflow that owns this token")
	@ApiResponse(responseCode = "202", description = "Accepted: the run is queued")
	@ApiResponse(responseCode = "404", description = "Unknown webhook",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Workflow is not active",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "413", description = "Body too large",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<WebhookAccepted> receive(@PathVariable String token, HttpServletRequest request) {
		Map<String, Object> trigger = new LinkedHashMap<>();
		trigger.put("body", readJsonBody(request));
		trigger.put("query", query(request));
		trigger.put("headers", headers(request));
		UUID runId = webhookService.accept(token, trigger);
		return ResponseEntity.accepted().body(new WebhookAccepted(runId));
	}

	private Object readJsonBody(HttpServletRequest request) {
		byte[] body;
		try {
			body = request.getInputStream().readNBytes(maxBodyBytes + 1); // never read more than the limit
		} catch (IOException e) {
			throw new InvalidPayloadException(HttpStatus.BAD_REQUEST, "Could not read the request body");
		}
		if (body.length > maxBodyBytes) {
			throw new InvalidPayloadException(HttpStatus.PAYLOAD_TOO_LARGE, "Body is larger than " + maxBodyBytes + " bytes");
		}
		try {
			return jsonMapper.readValue(body, Object.class);
		} catch (JacksonException e) {
			throw new InvalidPayloadException(HttpStatus.BAD_REQUEST, "Body must be valid JSON");
		}
	}

	private static Map<String, Object> query(HttpServletRequest request) {
		Map<String, Object> query = new LinkedHashMap<>();
		request.getParameterMap().forEach((name, values) -> query.put(name, values.length == 1 ? values[0] : List.of(values)));
		return query;
	}

	private static Map<String, Object> headers(HttpServletRequest request) {
		Map<String, Object> headers = new LinkedHashMap<>();
		for (String name : Collections.list(request.getHeaderNames())) {
			String key = name.toLowerCase(Locale.ROOT);
			if (!NEVER_STORED_HEADERS.contains(key)) {
				headers.put(key, String.join(", ", Collections.list(request.getHeaders(name))));
			}
		}
		return headers;
	}
}
