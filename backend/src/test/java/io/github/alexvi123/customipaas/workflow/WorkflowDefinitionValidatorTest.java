package io.github.alexvi123.customipaas.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.Connector;
import io.github.alexvi123.customipaas.connector.ConnectorInfo;
import io.github.alexvi123.customipaas.connector.ConnectorRegistry;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.InputValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WorkflowDefinitionValidatorTest {

	private static final Connector DEMO = new Connector() {
		@Override
		public ConnectorInfo info() {
			return new ConnectorInfo("demo", "Demo", "Demo");
		}

		@Override
		public List<ActionDefinition> actions() {
			return List.of(new ActionDefinition("send", "Send", "Send", List.of(
					FieldDefinition.text("text", "Text").asRequired(),
					FieldDefinition.number("count", "Count"))));
		}

		@Override
		public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
			return ActionResult.success(Map.of());
		}
	};

	private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(
			new ConnectorRegistry(List.of(DEMO)), new InputValidator(JsonMapper.builder().build()));

	private static Map<String, Object> workflow(Object... steps) {
		return Map.of("trigger", Map.of("type", "webhook"), "steps", List.of(steps));
	}

	private static Map<String, Object> step(String key, Map<String, Object> inputs) {
		return Map.of("key", key, "connector", "demo", "action", "send", "inputs", inputs);
	}

	private Map<String, String> errors(Map<String, Object> raw) {
		return catchThrowableOfType(InvalidWorkflowException.class, () -> validator.validate(raw)).errors();
	}

	@Test
	void acceptsAValidWorkflowAndKeepsTemplates() {
		WorkflowDefinition definition = validator.validate(workflow(
				step("first", Map.of("text", "Hi {{trigger.body.name}}")),
				step("second", Map.of("text", "{{steps.first.output.id}}", "count", 2))));

		assertThat(definition.steps()).extracting(StepDefinition::key).containsExactly("first", "second");
		assertThat(definition.steps().get(0).inputs()).containsEntry("text", "Hi {{trigger.body.name}}");
	}

	@Test
	void requiresAWebhookTriggerAndSteps() {
		assertThat(errors(Map.of("trigger", Map.of("type", "cron"), "steps", List.of())))
				.containsEntry("trigger.type", "Must be \"webhook\"")
				.containsKey("steps");
	}

	@Test
	void reportsUnknownConnectorsActionsAndFields() {
		Map<String, String> errors = errors(workflow(
				Map.of("key", "a", "connector", "nope", "action", "send"),
				Map.of("key", "b", "connector", "demo", "action", "fly"),
				step("c", Map.of("text", "x", "colour", "red"))));

		assertThat(errors)
				.containsEntry("steps[0].connector", "Unknown connector 'nope'")
				.containsEntry("steps[1].action", "Unknown action 'fly' for connector 'demo'")
				.containsEntry("steps[2].inputs.colour", "Unknown field");
	}

	@Test
	void checksRequiredFieldsAndPlainValues() {
		assertThat(errors(workflow(step("a", Map.of("count", "abc")))))
				.containsEntry("steps[0].inputs.text", "Required")
				.containsEntry("steps[0].inputs.count", "Must be a number");
	}

	@Test
	void templatedValuesSkipTypeChecks() {
		validator.validate(workflow(step("a", Map.of("text", "x", "count", "{{trigger.body.count}}"))));
	}

	@Test
	void rejectsDuplicateAndBadStepKeys() {
		assertThat(errors(workflow(step("same", Map.of("text", "x")), step("same", Map.of("text", "y")), step("1bad", Map.of("text", "z")))))
				.containsEntry("steps[1].key", "Duplicate step key 'same'")
				.containsKey("steps[2].key");
	}

	@Test
	void templatesMayOnlyUseEarlierSteps() {
		Map<String, String> errors = errors(workflow(
				step("first", Map.of("text", "{{steps.second.output.id}}")),
				step("second", Map.of("text", "{{steps.first.id}}"))));

		assertThat(errors.get("steps[0].inputs.text")).contains("doesn't run before this step");
		assertThat(errors).containsEntry("steps[1].inputs.text", "Use {{steps.<key>.output...}}");
	}
}
