package io.github.alexvi123.customipaas.connector;

import java.util.Map;

/** Context for loading dynamic options: credentials plus the form values filled in so far. */
public record OptionsContext(Credentials credentials, Map<String, Object> values) {

	public OptionsContext {
		values = Map.copyOf(values);
	}
}
