package io.github.alexvi123.customipaas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CustomIpaasApplicationTests {

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayAppliedTheBaselineMigration() {
		Long applied = jdbcClient
				.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
				.query(Long.class)
				.single();

		assertThat(applied).isEqualTo(1L);
	}

}
