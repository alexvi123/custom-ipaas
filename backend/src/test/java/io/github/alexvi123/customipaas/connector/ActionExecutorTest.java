package io.github.alexvi123.customipaas.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class ActionExecutorTest {

	private static final Map<String, Object> VALID = Map.of("message", "hi");

	private static ActionExecutor executorFor(Connector connector) {
		return new ActionExecutor(new ConnectorRegistry(List.of(connector)),
				new InputValidator(JsonMapper.builder().build()), new CredentialsProvider(new MockEnvironment()));
	}

	@Test
	void aSlowActionTimesOutAsARetryableFailure() {
		TestConnectors.FakeConnector slow = TestConnectors.echo(input -> {
			try {
				Thread.sleep(2_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return ActionResult.success(Map.of());
		});

		long start = System.nanoTime();
		ActionRun run = executorFor(slow).run("fake", "echo", VALID, Duration.ofMillis(100));

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
		assertThat(run.result().success()).isFalse();
		assertThat(run.result().retryable()).isTrue();
		assertThat(run.result().error()).isEqualTo("Timed out after 100 ms");
	}

	@Test
	void aRetryableConnectorExceptionStaysRetryable() {
		TestConnectors.FakeConnector flaky = TestConnectors.echo(input -> {
			throw new ConnectorException("service unavailable", true);
		});

		ActionRun run = executorFor(flaky).run("fake", "echo", VALID, Duration.ofSeconds(5));

		assertThat(run.result().retryable()).isTrue();
		assertThat(run.result().error()).isEqualTo("service unavailable");
	}

	@Test
	void bugsInTheConnectorAreThrown() {
		TestConnectors.FakeConnector broken = TestConnectors.echo(input -> {
			throw new IllegalStateException("bug");
		});

		assertThatThrownBy(() -> executorFor(broken).run("fake", "echo", VALID, Duration.ofSeconds(5)))
				.isInstanceOf(IllegalStateException.class).hasMessage("bug");
	}

	@Test
	void invalidInputIsRejectedBeforeTheActionRuns() {
		TestConnectors.FakeConnector connector = TestConnectors.echo(input -> ActionResult.success(Map.of()));

		assertThatThrownBy(() -> executorFor(connector).run("fake", "echo", Map.of(), Duration.ofSeconds(5)))
				.isInstanceOf(InvalidInputException.class);
		assertThat(connector.executions).isZero();
	}
}
