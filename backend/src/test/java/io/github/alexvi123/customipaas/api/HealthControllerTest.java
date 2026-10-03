package io.github.alexvi123.customipaas.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.alexvi123.customipaas.health.ComponentStatus;
import io.github.alexvi123.customipaas.health.HealthReport;
import io.github.alexvi123.customipaas.health.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthService healthService;

	@Test
	void returns200WhenEverythingIsUp() throws Exception {
		when(healthService.check()).thenReturn(new HealthReport(ComponentStatus.UP, ComponentStatus.UP));

		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void returns503WhenTheDatabaseIsDown() throws Exception {
		when(healthService.check()).thenReturn(new HealthReport(ComponentStatus.DOWN, ComponentStatus.DOWN));

		mockMvc.perform(get("/api/health"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.database").value("DOWN"));
	}

}
