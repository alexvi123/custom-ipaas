package io.github.alexvi123.customipaas.connectors.telegram;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionNotFoundException;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.Connector;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorInfo;
import io.github.alexvi123.customipaas.connector.Credentials;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.FieldNotFoundException;
import io.github.alexvi123.customipaas.connector.Option;
import io.github.alexvi123.customipaas.connector.OptionsContext;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends messages through a Telegram bot. The token goes in the request URL, so never log URLs or
 * Spring's exception messages here (they contain the URL).
 */
@Component
public class TelegramConnector implements Connector {

	private static final Logger log = LoggerFactory.getLogger(TelegramConnector.class);

	private static final int MAX_TEXT_LENGTH = 4096;
	private static final String MISSING_TOKEN = "Telegram bot token not configured: set the TELEGRAM_BOT_TOKEN "
			+ "environment variable and restart the backend";
	private static final List<String> MESSAGE_FIELDS =
			List.of("message", "edited_message", "channel_post", "edited_channel_post");

	private static final ActionDefinition SEND_MESSAGE = new ActionDefinition("sendMessage", "Send message",
			"Sends a text message to a chat",
			List.of(
					FieldDefinition.select("chatId", "Chat").asRequired().withDynamicOptions().withCustomValue()
							.withHelp("Send any message to your bot, then click Refresh, or type a chat ID."),
					FieldDefinition.text("text", "Text").asRequired().withHelp("Up to 4096 characters")));

	private final RestClient restClient;
	private final JsonMapper jsonMapper;
	private final String baseUrl;

	public TelegramConnector(RestClient connectorRestClient, JsonMapper jsonMapper,
			@Value("${ipaas.connectors.telegram.base-url}") String baseUrl) {
		this.restClient = connectorRestClient;
		this.jsonMapper = jsonMapper;
		this.baseUrl = baseUrl;
	}

	@Override
	public ConnectorInfo info() {
		return new ConnectorInfo("telegram", "Telegram", "Send messages with a Telegram bot");
	}

	@Override
	public List<ActionDefinition> actions() {
		return List.of(SEND_MESSAGE);
	}

	@Override
	public List<Option> dynamicOptions(String actionKey, String fieldKey, OptionsContext context) {
		if (!SEND_MESSAGE.key().equals(actionKey) || !"chatId".equals(fieldKey)) {
			throw new FieldNotFoundException("telegram", actionKey, fieldKey);
		}
		JsonNode updates = call(token(context.credentials()), "getUpdates", HttpMethod.GET, null);

		List<JsonNode> latestFirst = new ArrayList<>();
		updates.forEach(latestFirst::add);
		Collections.reverse(latestFirst);

		Map<String, Option> chatsById = new LinkedHashMap<>();
		for (JsonNode update : latestFirst) {
			MESSAGE_FIELDS.stream()
					.map(field -> update.path(field).path("chat"))
					.filter(chat -> !chat.isMissingNode())
					.findFirst()
					.ifPresent(chat -> chatsById.putIfAbsent(chat.path("id").asString(), new Option(chat.path("id").asString(), label(chat))));
		}
		return List.copyOf(chatsById.values());
	}

	@Override
	public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
		if (!SEND_MESSAGE.key().equals(actionKey)) {
			throw new ActionNotFoundException("telegram", actionKey);
		}
		String text = input.requireString("text");
		if (text.length() > MAX_TEXT_LENGTH) {
			return ActionResult.failure("Text is too long: " + text.length() + " characters (Telegram allows 4096)");
		}
		String token = token(context.credentials());
		try {
			JsonNode message = call(token, "sendMessage", HttpMethod.POST,
					Map.of("chat_id", input.requireString("chatId"), "text", text));
			Map<String, Object> output = new LinkedHashMap<>();
			output.put("messageId", message.path("message_id").asLong());
			output.put("chatId", message.path("chat").path("id").asLong());
			return ActionResult.success(output);
		} catch (ConnectorException e) {
			return ActionResult.failure(e.getMessage());
		}
	}

	private static String token(Credentials credentials) {
		return credentials.require("bot-token", MISSING_TOKEN);
	}

	private static String label(JsonNode chat) {
		String type = chat.path("type").asString();
		if (chat.hasNonNull("title")) {
			return chat.path("title").asString() + " · " + type;
		}
		String name = (chat.path("first_name").asString("") + " " + chat.path("last_name").asString("")).trim();
		if (chat.hasNonNull("username")) {
			name += " (@" + chat.path("username").asString() + ")";
		}
		return name + " · " + type;
	}

	/** Calls a Bot API method and returns its "result", or throws ConnectorException with Telegram's description. */
	private JsonNode call(String token, String method, HttpMethod httpMethod, Map<String, Object> body) {
		URI uri = URI.create(baseUrl + "/bot" + token + "/" + method);
		try {
			RestClient.RequestBodySpec request = restClient.method(httpMethod).uri(uri);
			if (body != null) {
				request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
			}
			byte[] responseBody = request.exchange((req, response) -> response.getBody().readAllBytes());
			JsonNode root = jsonMapper.readTree(responseBody);
			if (root.path("ok").asBoolean(false)) {
				return root.path("result");
			}
			String description = root.path("description").asString("unknown error");
			log.warn("Telegram {} failed: {}", method, description);
			throw new ConnectorException("Telegram: " + description);
		} catch (RestClientException e) {
			log.warn("Telegram {} could not be reached: {}", method, rootCause(e).getClass().getSimpleName());
			throw new ConnectorException("Could not reach Telegram (" + rootCause(e).getClass().getSimpleName() + ")");
		} catch (JacksonException e) {
			throw new ConnectorException("Telegram returned an unexpected response");
		}
	}

	private static Throwable rootCause(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root;
	}
}
