package io.github.alexvi123.customipaas.execution;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Retry and timeout defaults for workflow steps ({@code ipaas.execution.*}); a step can override some of them. */
@ConfigurationProperties("ipaas.execution")
public record ExecutionProperties(int maxAttempts, Duration baseDelay, Duration maxDelay, Duration stepTimeout) {

	public ExecutionProperties {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("ipaas.execution.max-attempts must be at least 1");
		}
		requirePositive(baseDelay, "base-delay");
		requirePositive(maxDelay, "max-delay");
		requirePositive(stepTimeout, "step-timeout");
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isNegative() || value.isZero()) {
			throw new IllegalArgumentException("ipaas.execution." + name + " must be a positive duration");
		}
	}
}
