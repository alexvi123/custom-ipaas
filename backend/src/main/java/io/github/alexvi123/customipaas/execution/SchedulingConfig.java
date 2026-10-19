package io.github.alexvi123.customipaas.execution;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ExecutionProperties.class)
public class SchedulingConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
