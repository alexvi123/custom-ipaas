package io.github.alexvi123.customipaas.connector;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Looks up a connector's credentials in configuration: {@code ipaas.credentials.<connector>.<name>},
 */
@Component
public class CredentialsProvider {

	private final Environment environment;

	public CredentialsProvider(Environment environment) {
		this.environment = environment;
	}

	public Credentials forConnector(String connectorKey) {
		return name -> Optional.ofNullable(environment.getProperty("ipaas.credentials." + connectorKey + "." + name))
				.filter(value -> !value.isBlank());
	}
}
