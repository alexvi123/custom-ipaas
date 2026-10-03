package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.health.ComponentStatus;
import io.github.alexvi123.customipaas.health.HealthReport;
import io.github.alexvi123.customipaas.health.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Report whether the backend and its database are up")
	@ApiResponse(responseCode = "200", description = "Backend and database are up",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = HealthReport.class)))
	@ApiResponse(responseCode = "503", description = "A dependency (the database) is down",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = HealthReport.class)))
	public ResponseEntity<HealthReport> health() {
		HealthReport report = healthService.check();
		HttpStatus httpStatus = report.status() == ComponentStatus.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		return ResponseEntity.status(httpStatus).body(report);
	}
}
