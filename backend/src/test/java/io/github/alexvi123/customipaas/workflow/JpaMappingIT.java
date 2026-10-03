package io.github.alexvi123.customipaas.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.alexvi123.customipaas.TestcontainersConfiguration;
import io.github.alexvi123.customipaas.execution.Run;
import io.github.alexvi123.customipaas.execution.RunRepository;
import io.github.alexvi123.customipaas.execution.RunStatus;
import io.github.alexvi123.customipaas.execution.StepRun;
import io.github.alexvi123.customipaas.execution.StepRunRepository;
import io.github.alexvi123.customipaas.execution.StepStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class JpaMappingIT {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	private WorkflowRepository workflows;

	@Autowired
	private WorkflowVersionRepository versions;

	@Autowired
	private RunRepository runs;

	@Autowired
	private StepRunRepository stepRuns;

	@Autowired
	private EntityManager entityManager;

	@Test
	void storesAndReadsJsonDefinitions() {
		Workflow workflow = workflows.save(new Workflow("Demo", "token-1", NOW));
		Map<String, Object> definition = Map.of(
				"trigger", Map.of("type", "webhook"),
				"steps", List.of(Map.of("key", "notify", "inputs", Map.of("count", 3, "text", "hi"))));
		versions.save(new WorkflowVersion(workflow.getId(), 1, definition, NOW));
		entityManager.flush();
		entityManager.clear();

		WorkflowVersion loaded = versions.findByWorkflowIdAndVersion(workflow.getId(), 1).orElseThrow();

		assertThat(loaded.getDefinition()).isEqualTo(definition);
		assertThat(workflows.findByWebhookToken("token-1")).isPresent();
	}

	@Test
	void storesRunsAndStepRuns() {
		Workflow workflow = workflows.save(new Workflow("Demo", "token-2", NOW));
		WorkflowVersion version = versions.save(new WorkflowVersion(workflow.getId(), 1, Map.of("steps", List.of()), NOW));
		Run run = runs.save(new Run(workflow.getId(), version.getId(), Map.of("body", Map.of("name", "Alex")), NOW));
		StepRun step = stepRuns.save(new StepRun(run.getId(), "notify", 0, "telegram", "sendMessage"));
		step.start(Map.of("text", "hello"), NOW);
		step.finish(StepStatus.SUCCEEDED, Map.of("messageId", 7), null, NOW);
		entityManager.flush();
		entityManager.clear();

		List<Run> recent = runs.findByWorkflowIdAndCreatedAtBeforeOrderByCreatedAtDesc(
				workflow.getId(), NOW.plusSeconds(1), Limit.of(20));
		List<StepRun> steps = stepRuns.findByRunIdOrderByPosition(run.getId());

		assertThat(recent).singleElement().satisfies(r -> {
			assertThat(r.getStatus()).isEqualTo(RunStatus.PENDING);
			assertThat(r.getTriggerPayload()).isEqualTo(Map.of("body", Map.of("name", "Alex")));
		});
		assertThat(steps).singleElement().satisfies(s -> {
			assertThat(s.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
			assertThat(s.getOutput()).isEqualTo(Map.of("messageId", 7));
		});
	}
}
