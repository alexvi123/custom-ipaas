package io.github.alexvi123.customipaas.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.alexvi123.customipaas.execution.Run;
import io.github.alexvi123.customipaas.execution.RunNotFoundException;
import io.github.alexvi123.customipaas.execution.RunNotRetryableException;
import io.github.alexvi123.customipaas.execution.RunQueryService;
import io.github.alexvi123.customipaas.execution.RunRetryService;
import io.github.alexvi123.customipaas.execution.RunStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RunController.class)
class RunControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RunQueryService runQueryService;

	@MockitoBean
	private RunRetryService runRetryService;

	private final UUID runId = UUID.randomUUID();

	@Test
	void retryQueuesTheRunAgain() throws Exception {
		Run requeued = new Run(UUID.randomUUID(), UUID.randomUUID(), Map.of(), null, Instant.now());
		when(runRetryService.retry(runId)).thenReturn(requeued);

		mockMvc.perform(post("/api/runs/" + runId + "/retry"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void retryingAnUnknownRunIsA404() throws Exception {
		when(runRetryService.retry(runId)).thenThrow(new RunNotFoundException(runId));

		mockMvc.perform(post("/api/runs/" + runId + "/retry"))
				.andExpect(status().isNotFound());
	}

	@Test
	void retryingARunThatHasNotFailedIsA409() throws Exception {
		when(runRetryService.retry(runId)).thenThrow(new RunNotRetryableException(RunStatus.SUCCEEDED));

		mockMvc.perform(post("/api/runs/" + runId + "/retry"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Run not retryable"))
				.andExpect(jsonPath("$.detail").value("Only failed runs can be retried; this run is SUCCEEDED"));
	}
}
