package io.github.alexvi123.customipaas.execution;

import java.time.Duration;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * How long to wait before retrying a step: doubles with every failed attempt (capped), then "equal jitter" picks a
 * random point in the upper half so many failing runs don't all retry at the same moment.
 */
@Component
public class Backoff {

	private final Duration baseDelay;
	private final Duration maxDelay;
	private final RandomGenerator random;

	@Autowired
	public Backoff(ExecutionProperties properties) {
		this(properties, new Random()); // java.util.Random is thread-safe
	}

	Backoff(ExecutionProperties properties, RandomGenerator random) {
		this.baseDelay = properties.baseDelay();
		this.maxDelay = properties.maxDelay();
		this.random = random;
	}

	public Duration delayAfter(int failedAttempt) {
		int doublings = Math.clamp(failedAttempt - 1, 0, 30);
		long exponential = Math.min(maxDelay.toMillis(), baseDelay.toMillis() * (1L << doublings));
		long half = exponential / 2;
		return Duration.ofMillis(half + random.nextLong(exponential - half + 1));
	}
}
