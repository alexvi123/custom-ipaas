package io.github.alexvi123.customipaas.api;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.alexvi123.customipaas.workflow.InvalidWorkflowException;
import io.github.alexvi123.customipaas.workflow.Workflow;
import io.github.alexvi123.customipaas.workflow.WorkflowNotFoundException;
import io.github.alexvi123.customipaas.workflow.WorkflowService;
import io.github.alexvi123.customipaas.workflow.WorkflowVersion;
import io.github.alexvi123.customipaas.workflow.WorkflowWithVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

	private static final String BODY = """
			{"name": "Demo", "definition": {"trigger": {"type": "webhook"}, "steps": []}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WorkflowService workflowService;

	@Test
	void createReturns201WithTheWebhookPath() throws Exception {
		Workflow workflow = new Workflow("Demo", "secret-token", Instant.now());
		when(workflowService.create(eq("Demo"), anyMap())).thenReturn(new WorkflowWithVersion(workflow,
				new WorkflowVersion(UUID.randomUUID(), 1, Map.of("steps", List.of()), Instant.now())));

		mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.webhookPath").value("/hooks/secret-token"))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void invalidDefinitionIsA400WithErrorsPerPath() throws Exception {
		when(workflowService.create(eq("Demo"), anyMap()))
				.thenThrow(new InvalidWorkflowException(Map.of("steps[0].connector", "Unknown connector 'nope'")));

		mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['steps[0].connector']").value("Unknown connector 'nope'"));
	}

	@Test
	void blankNameIsA400() throws Exception {
		mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \" \", \"definition\": {}}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(workflowService);
	}

	@Test
	void unknownWorkflowIsA404() throws Exception {
		UUID id = UUID.randomUUID();
		when(workflowService.get(id)).thenThrow(new WorkflowNotFoundException(id));

		mockMvc.perform(get("/api/workflows/" + id)).andExpect(status().isNotFound());
	}
}
