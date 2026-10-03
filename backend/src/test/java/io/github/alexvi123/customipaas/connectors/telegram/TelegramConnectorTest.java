package io.github.alexvi123.customipaas.connectors.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorHttpConfig;
import io.github.alexvi123.customipaas.connector.Credentials;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.Option;
import io.github.alexvi123.customipaas.connector.OptionsContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@WireMockTest
class TelegramConnectorTest {

	private static final Credentials TOKEN = name -> "bot-token".equals(name) ? Optional.of("test-token") : Optional.empty();
	private static final Credentials NO_TOKEN = name -> Optional.empty();

	private static TelegramConnector connector(String baseUrl) {
		return new TelegramConnector(new ConnectorHttpConfig().connectorRestClient(), JsonMapper.builder().build(), baseUrl);
	}

	private static ActionResult send(String baseUrl, String chatId, String text) {
		return connector(baseUrl).execute("sendMessage", new ActionInput(Map.of("chatId", chatId, "text", text)),
				new ExecutionContext(TOKEN));
	}

	@Test
	void sendsTheMessage(WireMockRuntimeInfo wireMock) {
		stubFor(post("/bottest-token/sendMessage").willReturn(okJson(
				"{\"ok\":true,\"result\":{\"message_id\":7,\"chat\":{\"id\":42,\"type\":\"private\"}}}")));

		ActionResult result = send(wireMock.getHttpBaseUrl(), "42", "hello");

		assertThat(result.success()).isTrue();
		assertThat(result.output()).containsEntry("messageId", 7L).containsEntry("chatId", 42L);
		verify(postRequestedFor(urlEqualTo("/bottest-token/sendMessage"))
				.withRequestBody(equalToJson("{\"chat_id\":\"42\",\"text\":\"hello\"}")));
	}

	@Test
	void telegramErrorsBecomeFailures(WireMockRuntimeInfo wireMock) {
		stubFor(post("/bottest-token/sendMessage").willReturn(aResponse().withStatus(400)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}")));

		ActionResult result = send(wireMock.getHttpBaseUrl(), "999", "hello");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("Telegram: Bad Request: chat not found");
	}

	@Test
	void missingTokenExplainsHowToFixIt(WireMockRuntimeInfo wireMock) {
		TelegramConnector connector = connector(wireMock.getHttpBaseUrl());

		assertThatThrownBy(() -> connector.execute("sendMessage", new ActionInput(Map.of("chatId", "1", "text", "x")),
				new ExecutionContext(NO_TOKEN)))
				.isInstanceOf(ConnectorException.class)
				.hasMessageContaining("TELEGRAM_BOT_TOKEN");
		assertThatThrownBy(() -> connector.dynamicOptions("sendMessage", "chatId", new OptionsContext(NO_TOKEN, Map.of())))
				.isInstanceOf(ConnectorException.class)
				.hasMessageContaining("TELEGRAM_BOT_TOKEN");
	}

	@Test
	void tooLongTextFailsWithoutCallingTelegram(WireMockRuntimeInfo wireMock) {
		ActionResult result = send(wireMock.getHttpBaseUrl(), "42", "a".repeat(4097));

		assertThat(result.success()).isFalse();
		assertThat(result.error()).contains("too long");
		verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void listsRecentChatsLatestFirstWithoutDuplicates(WireMockRuntimeInfo wireMock) {
		stubFor(get("/bottest-token/getUpdates").willReturn(okJson("""
				{"ok":true,"result":[
				  {"update_id":1,"message":{"chat":{"id":42,"type":"private","first_name":"Alex","username":"alexvi"}}},
				  {"update_id":2,"message":{"chat":{"id":-100,"type":"group","title":"Family"}}},
				  {"update_id":3,"edited_message":{"chat":{"id":42,"type":"private","first_name":"Alex","username":"alexvi"}}}
				]}""")));

		List<Option> options = connector(wireMock.getHttpBaseUrl())
				.dynamicOptions("sendMessage", "chatId", new OptionsContext(TOKEN, Map.of()));

		assertThat(options).containsExactly(
				new Option("42", "Alex (@alexvi) · private"),
				new Option("-100", "Family · group"));
	}

	@Test
	void getUpdatesErrorIsReported(WireMockRuntimeInfo wireMock) {
		stubFor(get("/bottest-token/getUpdates").willReturn(aResponse().withStatus(409)
				.withBody("{\"ok\":false,\"error_code\":409,\"description\":\"Conflict: webhook is active\"}")));

		assertThatThrownBy(() -> connector(wireMock.getHttpBaseUrl())
				.dynamicOptions("sendMessage", "chatId", new OptionsContext(TOKEN, Map.of())))
				.isInstanceOf(ConnectorException.class)
				.hasMessage("Telegram: Conflict: webhook is active");
	}

	@Test
	void tokenNeverAppearsInErrors() {
		ActionResult result = send("http://127.0.0.1:1", "42", "hello");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).startsWith("Could not reach Telegram").doesNotContain("test-token");
	}
}
