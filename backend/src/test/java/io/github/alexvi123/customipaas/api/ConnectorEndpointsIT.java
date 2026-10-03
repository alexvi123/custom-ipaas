package io.github.alexvi123.customipaas.api;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.alexvi123.customipaas.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConnectorEndpointsIT {

	@RegisterExtension
	static WireMockExtension telegram = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void telegramProperties(DynamicPropertyRegistry registry) {
		registry.add("ipaas.connectors.telegram.base-url", telegram::baseUrl);
		registry.add("ipaas.credentials.telegram.bot-token", () -> "test-token");
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsBothConnectors() throws Exception {
		mockMvc.perform(get("/api/connectors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].key", contains("http", "telegram")));
	}

	@Test
	void telegramChatFieldIsADynamicSelectWithManualEntry() throws Exception {
		mockMvc.perform(get("/api/connectors/telegram"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actions[0].fields[0].key").value("chatId"))
				.andExpect(jsonPath("$.actions[0].fields[0].dynamicOptions").value(true))
				.andExpect(jsonPath("$.actions[0].fields[0].allowCustomValue").value(true));
	}

	@Test
	void loadsChatOptionsFromTelegram() throws Exception {
		telegram.stubFor(WireMock.get("/bottest-token/getUpdates").willReturn(okJson(
				"{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":{\"chat\":{\"id\":42,\"type\":\"private\",\"first_name\":\"Alex\"}}}]}")));

		mockMvc.perform(post("/api/connectors/telegram/actions/sendMessage/fields/chatId/options"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].value").value("42"))
				.andExpect(jsonPath("$[0].label").value("Alex · private"));
	}

	@Test
	void testStepSendsTheMessage() throws Exception {
		telegram.stubFor(WireMock.post("/bottest-token/sendMessage").willReturn(okJson(
				"{\"ok\":true,\"result\":{\"message_id\":5,\"chat\":{\"id\":42,\"type\":\"private\"}}}")));

		mockMvc.perform(post("/api/connectors/telegram/actions/sendMessage/test")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"values\":{\"chatId\":\"42\",\"text\":\"Hello from the IT\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.output.messageId").value(5));
	}

	@Test
	void openApiDocumentsTheConnectorEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/api/connectors/{key}/actions/{action}/test")));
	}
}
