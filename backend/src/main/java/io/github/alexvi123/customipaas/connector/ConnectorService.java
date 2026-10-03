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
	private final ActionExecutor actionExecutor;
	private final CredentialsProvider credentialsProvider;

	public ConnectorService(ConnectorRegistry registry, ActionExecutor actionExecutor,
			CredentialsProvider credentialsProvider) {
		this.registry = registry;
		this.actionExecutor = actionExecutor;
		this.credentialsProvider = credentialsProvider;
	}

	public List<Connector> list() {
		return registry.all();
	}

	public Connector get(String connectorKey) {
		return registry.get(connectorKey);
	}

	public List<Option> options(String connectorKey, String actionKey, String fieldKey, Map<String, Object> values) {
		ActionDefinition action = registry.action(connectorKey, actionKey);
		action.field(fieldKey)
				.filter(FieldDefinition::dynamicOptions)
				.orElseThrow(() -> new FieldNotFoundException(connectorKey, actionKey, fieldKey));

		OptionsContext context = new OptionsContext(credentialsProvider.forConnector(connectorKey), withoutNulls(values));
		return registry.get(connectorKey).dynamicOptions(actionKey, fieldKey, context);
	}

	public ActionRun test(String connectorKey, String actionKey, Map<String, Object> values) {
		ActionRun run = actionExecutor.run(connectorKey, actionKey, values);
		log.info("Test run {}.{} -> success={} in {} ms", connectorKey, actionKey, run.result().success(), run.durationMs());
		return run;
	}

	private static Map<String, Object> withoutNulls(Map<String, Object> values) {
		Map<String, Object> copy = new HashMap<>(values);
		copy.values().removeIf(Objects::isNull);
		return copy;
	}
}
