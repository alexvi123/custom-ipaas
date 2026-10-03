package io.github.alexvi123.customipaas.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.alexvi123.customipaas.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;


@SpringBootTest(properties = {"ipaas.worker.poll-interval=100ms", "ipaas.connectors.http.allow-private-networks=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkflowRunIT {

	@RegisterExtension
	static WireMockExtension external = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void fakeTelegram(DynamicPropertyRegistry registry) {
		registry.add("ipaas.connectors.telegram.base-url", external::baseUrl);
		registry.add("ipaas.credentials.telegram.bot-token", () -> "test-token");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void webhookRunsTheStepsWithTemplates() throws Exception {
		external.stubFor(WireMock.get("/status").willReturn(okJson("{\"healthy\": true}")));
		external.stubFor(WireMock.post("/bottest-token/sendMessage")
				.withRequestBody(matchingJsonPath("$.chat_id", equalTo("42")))
				.willReturn(okJson("{\"ok\":true,\"result\":{\"message_id\":9,\"chat\":{\"id\":42,\"type\":\"private\"}}}")));
		Map<String, Object> workflow = createActiveWorkflow("42");

		String runId = callWebhook(workflow, "{\"name\": \"Alex\"}").get("runId").toString();
		Map<String, Object> run = awaitFinished(runId);

		assertThat(run.get("status")).isEqualTo("SUCCEEDED");
		List<Map<String, Object>> steps = steps(run);
		assertThat(steps).extracting(step -> step.get("status")).containsExactly("SUCCEEDED", "SUCCEEDED");
		assertThat(steps.get(1).get("output")).isEqualTo(Map.of("messageId", 9, "chatId", 42));
		external.verify(postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(matchingJsonPath("$.text", equalTo("Got 200 for Alex"))));

		String runs = mockMvc.perform(get("/api/runs").param("workflowId", workflow.get("id").toString()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(runs).contains(runId);
	}

	@Test
	void aFailingStepFailsTheRun() throws Exception {
		external.stubFor(WireMock.get("/status").willReturn(okJson("{}")));
		external.stubFor(WireMock.post("/bottest-token/sendMessage")
				.withRequestBody(matchingJsonPath("$.chat_id", equalTo("999")))
				.willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
						.withBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}")));
		Map<String, Object> workflow = createActiveWorkflow("999");

		Map<String, Object> run = awaitFinished(callWebhook(workflow, "{\"name\": \"Alex\"}").get("runId").toString());

		assertThat(run.get("status")).isEqualTo("FAILED");
		assertThat(run.get("error")).isEqualTo("Step 'notify' failed: Telegram: Bad Request: chat not found");
		assertThat(steps(run)).extracting(step -> step.get("status")).containsExactly("SUCCEEDED", "FAILED");
	}

	@Test
	void inactiveWorkflowsRejectWebhooks() throws Exception {
		Map<String, Object> workflow = create("42");

		mockMvc.perform(post(workflow.get("webhookPath").toString()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict());
	}

	private Map<String, Object> create(String chatId) throws Exception {
		String body = """
				{"name": "Webhook to Telegram", "definition": {
				  "trigger": {"type": "webhook"},
				  "steps": [
				    {"key": "fetch", "connector": "http", "action": "request",
				     "inputs": {"method": "GET", "url": "%s/status"}},
				    {"key": "notify", "connector": "telegram", "action": "sendMessage",
				     "inputs": {"chatId": "%s", "text": "Got {{steps.fetch.output.status}} for {{trigger.body.name}}"}}
				  ]}}
				""".formatted(external.baseUrl(), chatId);
		return json(mockMvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()));
	}

	private Map<String, Object> createActiveWorkflow(String chatId) throws Exception {
		Map<String, Object> workflow = create(chatId);
		mockMvc.perform(post("/api/workflows/" + workflow.get("id") + "/activate")).andExpect(status().isOk());
		return workflow;
	}

	private Map<String, Object> callWebhook(Map<String, Object> workflow, String payload) throws Exception {
		return json(mockMvc.perform(post(workflow.get("webhookPath").toString())
						.contentType(MediaType.APPLICATION_JSON).content(payload))
				.andExpect(status().isAccepted()));
	}

	private Map<String, Object> awaitFinished(String runId) {
		return await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
				.until(() -> json(mockMvc.perform(get("/api/runs/" + runId))),
						run -> "SUCCEEDED".equals(run.get("status")) || "FAILED".equals(run.get("status")));
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> steps(Map<String, Object> run) {
		return (List<Map<String, Object>>) run.get("steps");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> json(ResultActions result) throws Exception {
		return jsonMapper.readValue(result.andReturn().getResponse().getContentAsString(), Map.class);
	}
}
