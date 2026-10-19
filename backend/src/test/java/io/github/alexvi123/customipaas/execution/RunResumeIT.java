package io.github.alexvi123.customipaas.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.alexvi123.customipaas.TestcontainersConfiguration;
import io.github.alexvi123.customipaas.trigger.WebhookService;
import io.github.alexvi123.customipaas.workflow.WorkflowService;
import io.github.alexvi123.customipaas.workflow.WorkflowWithVersion;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Simulates a worker that died mid-run, then checks that the next worker finishes the run from where it stopped. */
@SpringBootTest(properties = {"ipaas.worker.enabled=false", "ipaas.connectors.http.allow-private-networks=true"})
@Import(TestcontainersConfiguration.class)
class RunResumeIT {

	@RegisterExtension
	static WireMockExtension external = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void fakeTelegram(DynamicPropertyRegistry registry) {
		registry.add("ipaas.connectors.telegram.base-url", external::baseUrl);
		registry.add("ipaas.credentials.telegram.bot-token", () -> "test-token");
	}

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WebhookService webhookService;

	@Autowired
	private JobQueue jobQueue;

	@Autowired
	private RunExecutor runExecutor;

	@Autowired
	private RunQueryService runQueryService;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void aCrashedRunResumesWithoutRepeatingFinishedSteps() {
		external.stubFor(WireMock.post("/bottest-token/sendMessage")
				.willReturn(okJson("{\"ok\":true,\"result\":{\"message_id\":3,\"chat\":{\"id\":42,\"type\":\"private\"}}}")));
		WorkflowWithVersion workflow = workflowService.create("Resume demo", Map.of(
				"trigger", Map.of("type", "webhook"),
				"steps", List.of(
						Map.of("key", "fetch", "connector", "http", "action", "request",
								"inputs", Map.of("method", "GET", "url", external.baseUrl() + "/status")),
						Map.of("key", "notify", "connector", "telegram", "action", "sendMessage",
								"inputs", Map.of("chatId", "42",
										"text", "Got {{steps.fetch.output.status}} for {{trigger.body.name}}")))));
		workflowService.setActive(workflow.workflow().getId(), true);
		UUID runId = webhookService.accept(workflow.workflow().getWebhookToken(),
				Map.of("body", Map.of("name", "Alex")), null).runId();

		// The state a worker leaves behind when it dies during step 2: fetch done, notify started, lease expired.
		jdbc.sql("UPDATE runs SET status = 'RUNNING', started_at = now() WHERE id = :id").param("id", runId).update();
		jdbc.sql("""
				UPDATE step_runs SET status = 'SUCCEEDED', attempt = 1, output = '{"status": 200, "body": "", "truncated": false}'
				WHERE run_id = :id AND step_key = 'fetch'
				""").param("id", runId).update();
		jdbc.sql("UPDATE step_runs SET status = 'RUNNING', attempt = 1 WHERE run_id = :id AND step_key = 'notify'")
				.param("id", runId).update();
		jdbc.sql("UPDATE jobs SET locked_until = now() - interval '1 second', attempts = 1 WHERE run_id = :id")
				.param("id", runId).update();

		runExecutor.execute(jobQueue.claimNext(Duration.ofMinutes(5)).orElseThrow());

		RunQueryService.RunWithSteps run = runQueryService.get(runId);
		assertThat(run.run().getStatus()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(run.steps()).extracting(StepRun::getAttempt).containsExactly(1, 2);
		external.verify(0, getRequestedFor(anyUrl()));
		external.verify(1, postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(matchingJsonPath("$.text", equalTo("Got 200 for Alex"))));
		assertThat(jdbc.sql("SELECT count(*) FROM jobs WHERE run_id = :id").param("id", runId).query(Long.class).single())
				.isZero();
	}
}
