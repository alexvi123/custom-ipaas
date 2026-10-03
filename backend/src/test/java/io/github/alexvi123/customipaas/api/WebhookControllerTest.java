package io.github.alexvi123.customipaas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.alexvi123.customipaas.trigger.WebhookNotFoundException;
import io.github.alexvi123.customipaas.trigger.WebhookService;
import io.github.alexvi123.customipaas.trigger.WorkflowInactiveException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WebhookController.class, properties = "ipaas.webhooks.max-body-bytes=100")
class WebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WebhookService webhookService;

	@Test
	@SuppressWarnings("unchecked")
	void acceptsJsonAndQueuesARun() throws Exception {
		UUID runId = UUID.randomUUID();
		when(webhookService.accept(eq("tok"), anyMap())).thenReturn(runId);

		mockMvc.perform(post("/hooks/tok?source=test").contentType(MediaType.APPLICATION_JSON)
						.header("Authorization", "Bearer secret")
						.header("X-Event", "order.created")
						.content("{\"name\": \"Alex\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.runId").value(runId.toString()));

		ArgumentCaptor<Map<String, Object>> trigger = ArgumentCaptor.forClass(Map.class);
		verify(webhookService).accept(eq("tok"), trigger.capture());
		assertThat(trigger.getValue()).containsEntry("body", Map.of("name", "Alex"))
				.containsEntry("query", Map.of("source", "test"));
		Map<String, Object> headers = (Map<String, Object>) trigger.getValue().get("headers");
		assertThat(headers).containsEntry("x-event", "order.created").doesNotContainKey("authorization");
	}

	@Test
	void unknownTokenIsA404() throws Exception {
		when(webhookService.accept(eq("nope"), anyMap())).thenThrow(new WebhookNotFoundException());

		mockMvc.perform(post("/hooks/nope").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void inactiveWorkflowIsA409() throws Exception {
		when(webhookService.accept(eq("tok"), anyMap())).thenThrow(new WorkflowInactiveException("Demo"));

		mockMvc.perform(post("/hooks/tok").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Workflow 'Demo' is not active. Activate it to accept webhooks."));
	}

	@Test
	void nonJsonIsA415() throws Exception {
		mockMvc.perform(post("/hooks/tok").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(webhookService);
	}

	@Test
	void oversizedBodyIsA413() throws Exception {
		mockMvc.perform(post("/hooks/tok").contentType(MediaType.APPLICATION_JSON)
						.content("{\"data\": \"" + "x".repeat(200) + "\"}"))
				.andExpect(status().isPayloadTooLarge());
		verifyNoInteractions(webhookService);
	}

	@Test
	void invalidJsonIsA400() throws Exception {
		mockMvc.perform(post("/hooks/tok").contentType(MediaType.APPLICATION_JSON).content("{oops"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Body must be valid JSON"));
	}
}
