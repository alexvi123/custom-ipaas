package io.github.alexvi123.customipaas.connector;

import java.util.Optional;

@FunctionalInterface
public interface Credentials {

	Optional<String> get(String name);

	default String require(String name, String messageIfMissing) {
		return get(name).filter(value -> !value.isBlank()).orElseThrow(() -> new ConnectorException(messageIfMissing));
	}
}
