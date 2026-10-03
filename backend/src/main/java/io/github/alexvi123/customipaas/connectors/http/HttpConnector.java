package io.github.alexvi123.customipaas.connectors.http;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionNotFoundException;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.Connector;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorInfo;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.Option;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpConnector implements Connector {

	private static final int MAX_BODY_CHARS = 10_000;

	private static final ActionDefinition REQUEST = new ActionDefinition("request", "Send an HTTP request",
			"Calls a URL and returns the status code and response body",
			List.of(
					FieldDefinition.select("method", "Method", methodOption("GET"), methodOption("POST"),
							methodOption("PUT"), methodOption("PATCH"), methodOption("DELETE"))
							.asRequired().withDefault("GET"),
					FieldDefinition.string("url", "URL").asRequired().withPlaceholder("https://api.example.com/items"),
					FieldDefinition.json("headers", "Headers")
							.withHelp("JSON object, e.g. {\"Authorization\": \"Bearer ...\"}"),
					FieldDefinition.text("body", "Body").withHelp("Sent as the request body (POST, PUT, PATCH)")));

	private final RestClient restClient;
	private final UrlGuard urlGuard;

	public HttpConnector(RestClient connectorRestClient,
			@Value("${ipaas.connectors.http.allow-private-networks:false}") boolean allowPrivateNetworks) {
		this.restClient = connectorRestClient;
		this.urlGuard = new UrlGuard(allowPrivateNetworks);
	}

	private static Option methodOption(String method) {
		return new Option(method, method);
	}

	@Override
	public ConnectorInfo info() {
		return new ConnectorInfo("http", "HTTP", "Call any HTTP API");
	}

	@Override
	public List<ActionDefinition> actions() {
		return List.of(REQUEST);
	}

	@Override
	public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
		if (!REQUEST.key().equals(actionKey)) {
			throw new ActionNotFoundException("http", actionKey);
		}
		String url = input.requireString("url");
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return ActionResult.failure("Invalid URL: " + url);
		}
		try {
			urlGuard.check(uri);
		} catch (ConnectorException e) {
			return ActionResult.failure(e.getMessage());
		}
		Map<String, String> headers;
		try {
			headers = headers(input);
		} catch (IllegalArgumentException e) {
			return ActionResult.failure(e.getMessage());
		}

		try {
			RestClient.RequestBodySpec request = restClient.method(HttpMethod.valueOf(input.requireString("method")))
					.uri(uri)
					.headers(httpHeaders -> headers.forEach(httpHeaders::set));
			input.string("body").ifPresent(request::body);

			Map<String, Object> output = request.exchange((req, response) -> {
				String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("status", response.getStatusCode().value());
				result.put("body", body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) : body);
				result.put("truncated", body.length() > MAX_BODY_CHARS);
				return result;
			});

			int status = (int) output.get("status");
			return status >= 200 && status < 300 ? ActionResult.success(output) : ActionResult.failure("HTTP " + status, output);
		} catch (RestClientException e) {
			return ActionResult.failure("Request failed: " + rootCauseMessage(e));
		}
	}

	private static Map<String, String> headers(ActionInput input) {
		Object raw = input.raw("headers").orElse(Map.of());
		if (!(raw instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException("headers must be a JSON object of strings");
		}
		Map<String, String> headers = new LinkedHashMap<>();
		map.forEach((name, value) -> {
			if (!(value instanceof String text)) {
				throw new IllegalArgumentException("headers must be a JSON object of strings");
			}
			headers.put(name.toString(), text);
		});
		return headers;
	}

	private static String rootCauseMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}
}
