package io.github.alexvi123.customipaas.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Checks whether the application's dependencies are reachable. */
@Service
public class HealthService {

	private static final Logger log = LoggerFactory.getLogger(HealthService.class);

	private final JdbcClient jdbcClient;

	public HealthService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public HealthReport check() {
		try {
			jdbcClient.sql("SELECT 1").query(Integer.class).single();
			return new HealthReport(ComponentStatus.UP, ComponentStatus.UP);
		} catch (DataAccessException e) {
			log.warn("Database health check failed: {}", e.getMessage());
			return new HealthReport(ComponentStatus.DOWN, ComponentStatus.DOWN);
		}
	}
}
