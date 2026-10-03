package io.github.alexvi123.customipaas.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InputValidatorTest {

	private final InputValidator validator = new InputValidator(JsonMapper.builder().build());

	private Map<String, String> errorsFor(List<FieldDefinition> fields, Map<String, Object> values) {
		return catchThrowableOfType(InvalidInputException.class, () -> validator.validate(fields, values)).fieldErrors();
	}

	@Test
	void missingRequiredFieldIsAnError() {
		List<FieldDefinition> fields = List.of(FieldDefinition.string("url", "URL").asRequired());

		assertThat(errorsFor(fields, Map.of("url", "  "))).containsEntry("url", "Required");
	}

	@Test
	void missingOptionalFieldIsLeftOut() {
		List<FieldDefinition> fields = List.of(FieldDefinition.string("note", "Note"));

		assertThat(validator.validate(fields, Map.of())).isEmpty();
	}

	@Test
	void defaultValueIsUsedWhenMissing() {
		List<FieldDefinition> fields = List.of(FieldDefinition.string("method", "Method").withDefault("GET"));

		assertThat(validator.validate(fields, Map.of())).containsEntry("method", "GET");
	}

	@Test
	void unknownFieldIsAnError() {
		List<FieldDefinition> fields = List.of(FieldDefinition.string("url", "URL"));

		assertThat(errorsFor(fields, Map.of("surprise", "x"))).containsEntry("surprise", "Unknown field");
	}

	@Test
	void numbersAreParsed() {
		List<FieldDefinition> fields = List.of(FieldDefinition.number("count", "Count"));

		assertThat(validator.validate(fields, Map.of("count", "42.5"))).containsEntry("count", new BigDecimal("42.5"));
		assertThat(errorsFor(fields, Map.of("count", "abc"))).containsEntry("count", "Must be a number");
	}

	@Test
	void booleansAcceptTrueOrFalse() {
		List<FieldDefinition> fields = List.of(FieldDefinition.bool("enabled", "Enabled"));

		assertThat(validator.validate(fields, Map.of("enabled", "true"))).containsEntry("enabled", true);
		assertThat(errorsFor(fields, Map.of("enabled", "yes"))).containsEntry("enabled", "Must be true or false");
	}

	@Test
	void staticSelectMustBeOneOfTheOptions() {
		List<FieldDefinition> fields = List.of(FieldDefinition.select("method", "Method",
				new Option("GET", "GET"), new Option("POST", "POST")));

		assertThat(validator.validate(fields, Map.of("method", "POST"))).containsEntry("method", "POST");
		assertThat(errorsFor(fields, Map.of("method", "TRACE"))).containsEntry("method", "Must be one of: GET, POST");
	}

	@Test
	void dynamicOrCustomSelectAcceptsAnyValue() {
		List<FieldDefinition> fields = List.of(FieldDefinition.select("chatId", "Chat").withDynamicOptions().withCustomValue());

		assertThat(validator.validate(fields, Map.of("chatId", 123456))).containsEntry("chatId", "123456");
	}

	@Test
	void jsonTextIsParsed() {
		List<FieldDefinition> fields = List.of(FieldDefinition.json("headers", "Headers"));

		assertThat(validator.validate(fields, Map.of("headers", "{\"Accept\":\"text/plain\"}")))
				.containsEntry("headers", Map.of("Accept", "text/plain"));
		assertThat(errorsFor(fields, Map.of("headers", "{not json"))).containsEntry("headers", "Must be valid JSON");
	}

	@Test
	void reportsEveryInvalidFieldAtOnce() {
		List<FieldDefinition> fields = List.of(
				FieldDefinition.string("url", "URL").asRequired(),
				FieldDefinition.number("count", "Count"));
		Map<String, Object> values = new HashMap<>();
		values.put("url", null);
		values.put("count", "x");

		assertThat(errorsFor(fields, values)).containsOnlyKeys("url", "count");
	}
}
