package io.github.alexvi123.customipaas.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorRegistryTest {

	@Test
	void findsConnectorsByKey() {
		Connector http = TestConnectors.named("http");
		ConnectorRegistry registry = new ConnectorRegistry(List.of(http, TestConnectors.named("telegram")));

		assertThat(registry.get("http")).isSameAs(http);
	}

	@Test
	void listsConnectorsSortedByKey() {
		ConnectorRegistry registry = new ConnectorRegistry(
				List.of(TestConnectors.named("telegram"), TestConnectors.named("http")));

		assertThat(registry.all()).extracting(c -> c.info().key()).containsExactly("http", "telegram");
	}

	@Test
	void unknownKeyThrows() {
		ConnectorRegistry registry = new ConnectorRegistry(List.of(TestConnectors.named("http")));

		assertThatThrownBy(() -> registry.get("nope"))
				.isInstanceOf(ConnectorNotFoundException.class)
				.hasMessageContaining("nope");
	}

	@Test
	void duplicateKeysStopStartup() {
		List<Connector> connectors = List.of(TestConnectors.named("http"), TestConnectors.named("http"));

		assertThatThrownBy(() -> new ConnectorRegistry(connectors))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Duplicate connector key 'http'");
	}
}
