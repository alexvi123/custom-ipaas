package io.github.alexvi123.customipaas.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class ConnectorServiceTest {

	private ConnectorService serviceWith(Connector connector) {
		return new ConnectorService(new ConnectorRegistry(List.of(connector)),
				new InputValidator(JsonMapper.builder().build()),
				new CredentialsProvider(new MockEnvironment().withProperty("ipaas.credentials.fake.token", "secret")));
	}

	@Test
	void runsTheActionAndReportsSuccess() {
		TestConnectors.FakeConnector connector = TestConnectors.echo(
				input -> ActionResult.success(Map.of("echo", input.requireString("message"))));

		TestRun run = serviceWith(connector).test("fake", "echo", Map.of("message", "hi"));

		assertThat(run.result().success()).isTrue();
		assertThat(run.result().output()).containsEntry("echo", "hi");
		assertThat(run.durationMs()).isNotNegative();
	}

	@Test
	void invalidInputNeverReachesTheConnector() {
		TestConnectors.FakeConnector connector = TestConnectors.echo(input -> ActionResult.success(Map.of()));

		assertThatThrownBy(() -> serviceWith(connector).test("fake", "echo", Map.of()))
				.isInstanceOf(InvalidInputException.class);
		assertThat(connector.executions).isZero();
	}

	@Test
	void connectorExceptionBecomesAFailedRun() {
		TestConnectors.FakeConnector connector = TestConnectors.echo(input -> {
			throw new ConnectorException("token not configured");
		});

		TestRun run = serviceWith(connector).test("fake", "echo", Map.of("message", "hi"));

		assertThat(run.result().success()).isFalse();
		assertThat(run.result().error()).isEqualTo("token not configured");
	}

	@Test
	void unknownActionThrows() {
		assertThatThrownBy(() -> serviceWith(TestConnectors.echo(i -> null)).test("fake", "nope", Map.of()))
				.isInstanceOf(ActionNotFoundException.class);
	}

	@Test
	void optionsOnlyForDynamicFields() {
		ConnectorService service = serviceWith(TestConnectors.echo(i -> null));

		assertThat(service.options("fake", "echo", "target", Map.of())).extracting(Option::value).containsExactly("a");
		assertThatThrownBy(() -> service.options("fake", "echo", "message", Map.of()))
				.isInstanceOf(FieldNotFoundException.class);
	}

	@Test
	void optionsReceiveCredentialsForTheConnector() {
		TestConnectors.FakeConnector connector = TestConnectors.echo(i -> null);

		serviceWith(connector).options("fake", "echo", "target", Map.of());

		assertThat(connector.lastOptionsContext.credentials().get("token")).contains("secret");
	}
}
