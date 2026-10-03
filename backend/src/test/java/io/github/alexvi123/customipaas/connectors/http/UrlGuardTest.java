package io.github.alexvi123.customipaas.connectors.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.alexvi123.customipaas.connector.ConnectorException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

	private final UrlGuard guard = new UrlGuard(false);

	@ParameterizedTest
	@ValueSource(strings = {
			"http://127.0.0.1/x",
			"http://localhost/x",
			"http://10.0.0.5/",
			"http://172.16.0.1/",
			"http://192.168.1.1/",
			"http://169.254.169.254/latest/meta-data",
			"http://0.0.0.0/",
			"http://100.64.0.1/",
			"http://[::1]/",
			"http://[fd00::1]/"
	})
	void blocksInternalAddresses(String url) {
		assertThatThrownBy(() -> guard.check(URI.create(url)))
				.isInstanceOf(ConnectorException.class)
				.hasMessageContaining("Blocked");
	}

	@Test
	void allowsPublicAddresses() {
		assertThatCode(() -> guard.check(URI.create("http://93.184.216.34/"))).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ftp://example.com/file", "file:///etc/passwd"})
	void allowsOnlyHttpAndHttps(String url) {
		assertThatThrownBy(() -> guard.check(URI.create(url)))
				.isInstanceOf(ConnectorException.class)
				.hasMessageContaining("Only http and https");
	}

	@Test
	void privateNetworksCanBeAllowedExplicitly() {
		assertThatCode(() -> new UrlGuard(true).check(URI.create("http://127.0.0.1/"))).doesNotThrowAnyException();
	}
}
