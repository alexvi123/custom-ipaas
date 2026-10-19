package io.github.alexvi123.customipaas.connector;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Validates input and runs one connector action, giving up after a timeout. Shared by the Test step button and the
 * workflow worker. The action runs on its own virtual thread so the caller can stop waiting for it.
 */
@Component
public class ActionExecutor {

	private final ConnectorRegistry registry;
	private final InputValidator inputValidator;
	private final CredentialsProvider credentialsProvider;
	private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

	public ActionExecutor(ConnectorRegistry registry, InputValidator inputValidator,
			CredentialsProvider credentialsProvider) {
		this.registry = registry;
		this.inputValidator = inputValidator;
		this.credentialsProvider = credentialsProvider;
	}

	/**
	 * @throws InvalidInputException when values don't match the action's fields (the action is not called)
	 */
	public ActionRun run(String connectorKey, String actionKey, Map<String, Object> values, Duration timeout) {
		Connector connector = registry.get(connectorKey);
		ActionDefinition action = registry.action(connectorKey, actionKey);
		Map<String, Object> validValues = inputValidator.validate(action.fields(), values);
		ExecutionContext context = new ExecutionContext(credentialsProvider.forConnector(connectorKey));

		long start = System.nanoTime();
		Future<ActionResult> future = threads.submit(() -> connector.execute(actionKey, new ActionInput(validValues), context));
		ActionResult result;
		try {
			result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(true); // interrupts the call; a connector that ignores interrupts finishes unseen
			result = ActionResult.retryableFailure("Timed out after " + timeout.toMillis() + " ms");
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ConnectorException connectorException) {
				result = ActionResult.from(connectorException); // e.g. missing token
			} else if (e.getCause() instanceof RuntimeException bug) {
				throw bug;
			} else {
				throw new IllegalStateException(e.getCause());
			}
		} catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for " + connectorKey + "." + actionKey, e);
		}
		return new ActionRun(result, (System.nanoTime() - start) / 1_000_000);
	}

	@PreDestroy
	void close() {
		threads.shutdownNow();
	}
}
