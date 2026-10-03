package io.github.alexvi123.customipaas.connector;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Validates input and runs one connector action. Shared by the Test step button and the workflow worker. */
@Component
public class ActionExecutor {

	private final ConnectorRegistry registry;
	private final InputValidator inputValidator;
	private final CredentialsProvider credentialsProvider;

	public ActionExecutor(ConnectorRegistry registry, InputValidator inputValidator,
			CredentialsProvider credentialsProvider) {
		this.registry = registry;
		this.inputValidator = inputValidator;
		this.credentialsProvider = credentialsProvider;
	}

	/**
	 * @throws InvalidInputException when values don't match the action's fields (the action is not called)
	 */
	public ActionRun run(String connectorKey, String actionKey, Map<String, Object> values) {
		Connector connector = registry.get(connectorKey);
		ActionDefinition action = registry.action(connectorKey, actionKey);
		Map<String, Object> validValues = inputValidator.validate(action.fields(), values);

		long start = System.nanoTime();
		ActionResult result;
		try {
			result = connector.execute(actionKey, new ActionInput(validValues),
					new ExecutionContext(credentialsProvider.forConnector(connectorKey)));
		} catch (ConnectorException e) {
			result = ActionResult.failure(e.getMessage()); // e.g. missing token
		}
		return new ActionRun(result, (System.nanoTime() - start) / 1_000_000);
	}
}
