package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InputRedactorTest {

	@Test
	void masksSecretLookingKeysAtAnyDepth() {
		Map<String, Object> redacted = InputRedactor.redact(Map.of(
				"url", "https://api.example.com",
				"headers", Map.of("Authorization", "Bearer abc", "X-Api-Key", "k", "Accept", "application/json"),
				"password", "hunter2"));

		assertThat(redacted).containsEntry("url", "https://api.example.com").containsEntry("password", "***");
		assertThat(redacted.get("headers")).isEqualTo(
				Map.of("Authorization", "***", "X-Api-Key", "***", "Accept", "application/json"));
	}
}
