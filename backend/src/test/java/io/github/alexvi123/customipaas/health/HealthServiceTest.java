package io.github.alexvi123.customipaas.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class HealthServiceTest {

	@Test
	void reportsDownWhenTheDatabaseIsUnreachable() throws SQLException {
		DataSource unreachableDatabase = mock(DataSource.class);
		when(unreachableDatabase.getConnection()).thenThrow(new SQLException("connection refused"));
		HealthService healthService = new HealthService(JdbcClient.create(unreachableDatabase));

		HealthReport report = healthService.check();

		assertThat(report).isEqualTo(new HealthReport(ComponentStatus.DOWN, ComponentStatus.DOWN));
	}

}
