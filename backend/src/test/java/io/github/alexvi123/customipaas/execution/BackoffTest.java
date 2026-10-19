package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class BackoffTest {

	private static final ExecutionProperties PROPERTIES =
			new ExecutionProperties(3, Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofSeconds(30));

	private final Backoff backoff = new Backoff(PROPERTIES, new Random());

	@RepeatedTest(20)
	void delayDoublesWithEveryAttemptWithJitterInTheUpperHalf() {
		assertThat(backoff.delayAfter(1)).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10));
		assertThat(backoff.delayAfter(2)).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(20));
		assertThat(backoff.delayAfter(3)).isBetween(Duration.ofSeconds(20), Duration.ofSeconds(40));
	}

	@RepeatedTest(20)
	void delayIsCappedAtMaxDelay() {
		assertThat(backoff.delayAfter(30)).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(10));
		assertThat(backoff.delayAfter(1_000)).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(10));
	}

	@Test
	void jitterSpreadsDelays() {
		long distinct = IntStream.range(0, 50).mapToObj(i -> backoff.delayAfter(1)).distinct().count();

		assertThat(distinct).isGreaterThan(1);
	}
}
