package io.github.alexvi123.customipaas.api;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.alexvi123.customipaas.connector.ActionDefinition;
import io.github.alexvi123.customipaas.connector.ActionInput;
import io.github.alexvi123.customipaas.connector.ActionResult;
import io.github.alexvi123.customipaas.connector.Connector;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorInfo;
import io.github.alexvi123.customipaas.connector.ConnectorNotFoundException;
import io.github.alexvi123.customipaas.connector.ConnectorService;
import io.github.alexvi123.customipaas.connector.ExecutionContext;
import io.github.alexvi123.customipaas.connector.FieldDefinition;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import io.github.alexvi123.customipaas.connector.TestRun;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectorController.class)
class ConnectorControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ConnectorService connectorService;

	private static final Connector DEMO = new Connector() {
		@Override
		public ConnectorInfo info() {
			return new ConnectorInfo("demo", "Demo", "A demo connector");
		}

		@Override
		public List<ActionDefinition> actions() {
			return List.of(new ActionDefinition("ping", "Ping", "Pings",
					List.of(FieldDefinition.string("url", "URL").asRequired())));
		}

		@Override
		public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
			return ActionResult.success(Map.of());
		}
	};

	@Test
	void listsConnectors() throws Exception {
		when(connectorService.list()).thenReturn(List.of(DEMO));

		mockMvc.perform(get("/api/connectors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].key").value("demo"))
				.andExpect(jsonPath("$[0].name").value("Demo"));
	}

	@Test
	void returnsFieldDefinitions() throws Exception {
		when(connectorService.get("demo")).thenReturn(DEMO);

		mockMvc.perform(get("/api/connectors/demo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actions[0].fields[0].key").value("url"))
				.andExpect(jsonPath("$.actions[0].fields[0].type").value("STRING"))
				.andExpect(jsonPath("$.actions[0].fields[0].required").value(true));
	}

	@Test
	void unknownConnectorIsA404ProblemDetail() throws Exception {
		when(connectorService.get("nope")).thenThrow(new ConnectorNotFoundException("nope"));

		mockMvc.perform(get("/api/connectors/nope"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Unknown connector 'nope'"));
	}

	@Test
	void invalidInputIsA400WithFieldErrors() throws Exception {
		when(connectorService.test(eq("demo"), eq("ping"), anyMap()))
				.thenThrow(new InvalidInputException(Map.of("url", "Required")));

		mockMvc.perform(post("/api/connectors/demo/actions/ping/test")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"values\":{}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.url").value("Required"));
	}

	@Test
	void missingValuesIsA400() throws Exception {
		mockMvc.perform(post("/api/connectors/demo/actions/ping/test")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(connectorService);
	}

	@Test
	void failedTestRunIsStillA200() throws Exception {
		when(connectorService.test(eq("demo"), eq("ping"), anyMap()))
				.thenReturn(new TestRun(ActionResult.failure("Telegram: chat not found"), 120));

		mockMvc.perform(post("/api/connectors/demo/actions/ping/test")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"values\":{\"url\":\"x\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error").value("Telegram: chat not found"))
				.andExpect(jsonPath("$.durationMs").value(120));
	}

	@Test
	void connectorFailureWhileLoadingOptionsIsA502() throws Exception {
		when(connectorService.options(eq("demo"), eq("ping"), eq("url"), anyMap()))
				.thenThrow(new ConnectorException("Telegram bot token not configured"));

		mockMvc.perform(post("/api/connectors/demo/actions/ping/fields/url/options"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Telegram bot token not configured"));
	}
}
