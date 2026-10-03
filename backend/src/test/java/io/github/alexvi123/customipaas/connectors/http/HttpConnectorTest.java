package io.github.alexvi123.customipaas.connectors.http;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.ConnectorHttpConfig;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@WireMockTest
class HttpConnectorTest {

	private static ActionResult run(boolean allowPrivateNetworks, Map<String, Object> values) {
		HttpConnector connector = new HttpConnector(new ConnectorHttpConfig().connectorRestClient(), allowPrivateNetworks);
		return connector.execute("request", new ActionInput(values), new ExecutionContext(name -> Optional.empty()));
	}

	@Test
	void getReturnsStatusAndBody(WireMockRuntimeInfo wireMock) {
		stubFor(get("/items").willReturn(okJson("[1,2]")));

		ActionResult result = run(true, Map.of("method", "GET", "url", wireMock.getHttpBaseUrl() + "/items"));

		assertThat(result.success()).isTrue();
		assertThat(result.output()).containsEntry("status", 200).containsEntry("body", "[1,2]");
	}

	@Test
	void postSendsHeadersAndBody(WireMockRuntimeInfo wireMock) {
		stubFor(post("/items").willReturn(created()));

		ActionResult result = run(true, Map.of(
				"method", "POST",
				"url", wireMock.getHttpBaseUrl() + "/items",
				"headers", Map.of("X-Api-Key", "secret"),
				"body", "{\"name\":\"test\"}"));

		assertThat(result.success()).isTrue();
		verify(postRequestedFor(urlEqualTo("/items"))
				.withHeader("X-Api-Key", equalTo("secret"))
				.withRequestBody(equalTo("{\"name\":\"test\"}")));
	}

	@Test
	void non2xxStatusIsAFailure(WireMockRuntimeInfo wireMock) {
		stubFor(get("/missing").willReturn(notFound()));

		ActionResult result = run(true, Map.of("method", "GET", "url", wireMock.getHttpBaseUrl() + "/missing"));

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("HTTP 404");
		assertThat(result.output()).containsEntry("status", 404);
	}

	@Test
	void unreachableServerIsAFailure() {
		ActionResult result = run(true, Map.of("method", "GET", "url", "http://127.0.0.1:1/nothing-here"));

		assertThat(result.success()).isFalse();
		assertThat(result.error()).startsWith("Request failed");
	}

	@Test
	void privateAddressesAreBlockedByDefault(WireMockRuntimeInfo wireMock) {
		ActionResult result = run(false, Map.of("method", "GET", "url", wireMock.getHttpBaseUrl() + "/items"));

		assertThat(result.success()).isFalse();
		assertThat(result.error()).contains("Blocked");
		verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void headersMustBeAnObjectOfStrings(WireMockRuntimeInfo wireMock) {
		ActionResult result = run(true, Map.of(
				"method", "GET",
				"url", wireMock.getHttpBaseUrl() + "/items",
				"headers", Map.of("X-Count", 3)));

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("headers must be a JSON object of strings");
	}
}
