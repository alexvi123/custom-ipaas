package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateResolverTest {

	private static final Map<String, Object> CONTEXT = Map.of(
			"trigger", Map.of("body", Map.of("name", "Alex", "count", 3, "items", List.of(Map.of("sku", "A1")))),
			"steps", Map.of("fetch", Map.of("output", Map.of("status", 200))));

	@Test
	void aWholePlaceholderKeepsTheReferencedType() {
		assertThat(TemplateResolver.resolve(Map.of("n", "{{trigger.body.count}}"), CONTEXT)).containsEntry("n", 3);
	}

	@Test
	void placeholdersInsideTextBecomeText() {
		Map<String, Object> resolved = TemplateResolver.resolve(
				Map.of("text", "Got {{steps.fetch.output.status}} for {{ trigger.body.name }}"), CONTEXT);

		assertThat(resolved).containsEntry("text", "Got 200 for Alex");
	}

	@Test
	void supportsArrayIndexesAndNestedInputs() {
		Map<String, Object> resolved = TemplateResolver.resolve(
				Map.of("headers", Map.of("X-Sku", "{{trigger.body.items.0.sku}}")), CONTEXT);

		assertThat(resolved).containsEntry("headers", Map.of("X-Sku", "A1"));
	}

	@Test
	void objectsInsideTextAreWrittenAsJson() {
		assertThat(TemplateResolver.resolve(Map.of("text", "Status: {{steps.fetch.output}}"), CONTEXT))
				.containsEntry("text", "Status: {\"status\":200}");
	}

	@Test
	void missingValueNamesTheTemplate() {
		assertThatThrownBy(() -> TemplateResolver.resolve(Map.of("x", "{{trigger.body.missing}}"), CONTEXT))
				.isInstanceOf(TemplateException.class)
				.hasMessage("Template {{trigger.body.missing}} has no value");
	}

	@Test
	void onlyTriggerAndStepsRootsAreAllowed() {
		assertThatThrownBy(() -> TemplateResolver.resolve(Map.of("x", "{{env.HOME}}"), CONTEXT))
				.isInstanceOf(TemplateException.class)
				.hasMessageContaining("must start with 'trigger' or 'steps'");
	}

	@Test
	void plainValuesAreUntouched() {
		assertThat(TemplateResolver.resolve(Map.of("a", "hello", "b", 5), CONTEXT)).containsEntry("a", "hello").containsEntry("b", 5);
	}

	@Test
	void findsReferencesInNestedValues() {
		assertThat(TemplateResolver.references(Map.of("h", List.of("{{trigger.body.a}}", "x {{steps.s.output.b}}"))))
				.containsExactlyInAnyOrder("trigger.body.a", "steps.s.output.b");
	}
}
