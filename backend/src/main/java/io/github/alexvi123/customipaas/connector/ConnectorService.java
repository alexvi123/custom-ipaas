package io.github.alexvi123.customipaas.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConnectorService {

	private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

	private final ConnectorRegistry registry;
	private final InputValidator inputValidator;
	private final CredentialsProvider credentialsProvider;

	public ConnectorService(ConnectorRegistry registry, InputValidator inputValidator,
			CredentialsProvider credentialsProvider) {
		this.registry = registry;
		this.inputValidator = inputValidator;
		this.credentialsProvider = credentialsProvider;
	}

	public List<Connector> list() {
		return registry.all();
	}

	public Connector get(String connectorKey) {
		return registry.get(connectorKey);
	}

	public List<Option> options(String connectorKey, String actionKey, String fieldKey, Map<String, Object> values) {
		Connector connector = registry.get(connectorKey);
		ActionDefinition action = findAction(connector, actionKey);
		action.field(fieldKey)
				.filter(FieldDefinition::dynamicOptions)
				.orElseThrow(() -> new FieldNotFoundException(connectorKey, actionKey, fieldKey));

		OptionsContext context = new OptionsContext(credentialsProvider.forConnector(connectorKey), withoutNulls(values));
		return connector.dynamicOptions(actionKey, fieldKey, context);
	}

	public TestRun test(String connectorKey, String actionKey, Map<String, Object> values) {
		Connector connector = registry.get(connectorKey);
		ActionDefinition action = findAction(connector, actionKey);
		Map<String, Object> validValues = inputValidator.validate(action.fields(), values);

		long start = System.nanoTime();
		ActionResult result;
		try {
			result = connector.execute(actionKey, new ActionInput(validValues),
					new ExecutionContext(credentialsProvider.forConnector(connectorKey)));
		} catch (ConnectorException e) {
			// (e.g. missing token).
			result = ActionResult.failure(e.getMessage());
		}
		long durationMs = (System.nanoTime() - start) / 1_000_000;

		log.info("Test run {}.{} -> success={} in {} ms", connectorKey, actionKey, result.success(), durationMs);
		return new TestRun(result, durationMs);
	}

	private static ActionDefinition findAction(Connector connector, String actionKey) {
		return connector.actions().stream()
				.filter(action -> action.key().equals(actionKey))
				.findFirst()
				.orElseThrow(() -> new ActionNotFoundException(connector.info().key(), actionKey));
	}

	private static Map<String, Object> withoutNulls(Map<String, Object> values) {
		Map<String, Object> copy = new HashMap<>(values);
		copy.values().removeIf(Objects::isNull);
		return copy;
	}
}
