package io.github.alexvi123.customipaas.connector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** All connectors in the app, found automatically: Spring injects every bean implementing {@link Connector}. */
@Component
public class ConnectorRegistry {

	private final Map<String, Connector> connectorsByKey;

	public ConnectorRegistry(List<Connector> connectors) {
		Map<String, Connector> byKey = new TreeMap<>();
		for (Connector connector : connectors) {
			String key = connector.info().key();
			Connector previous = byKey.putIfAbsent(key, connector);
			if (previous != null) {
				throw new IllegalStateException("Duplicate connector key '" + key + "': "
						+ previous.getClass().getName() + " and " + connector.getClass().getName());
			}
		}
		this.connectorsByKey = Collections.unmodifiableMap(byKey);
	}

	/** All connectors, sorted by key. */
	public List<Connector> all() {
		return List.copyOf(connectorsByKey.values());
	}

	public Connector get(String key) {
		Connector connector = connectorsByKey.get(key);
		if (connector == null) {
			throw new ConnectorNotFoundException(key);
		}
		return connector;
	}

	public ActionDefinition action(String connectorKey, String actionKey) {
		return get(connectorKey).actions().stream()
				.filter(action -> action.key().equals(actionKey))
				.findFirst()
				.orElseThrow(() -> new ActionNotFoundException(connectorKey, actionKey));
	}
}
