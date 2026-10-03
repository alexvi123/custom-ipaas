package io.github.alexvi123.customipaas.execution;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the job queue and runs whatever it claims. Off when ipaas.worker.enabled=false. */
@Component
@ConditionalOnProperty(name = "ipaas.worker.enabled", havingValue = "true", matchIfMissing = true)
public class Worker {

	private final JobQueue jobQueue;
	private final RunExecutor runExecutor;
	private final Duration lease;

	public Worker(JobQueue jobQueue, RunExecutor runExecutor, @Value("${ipaas.worker.lease}") Duration lease) {
		this.jobQueue = jobQueue;
		this.runExecutor = runExecutor;
		this.lease = lease;
	}

	@Scheduled(fixedDelayString = "${ipaas.worker.poll-interval}")
	public void poll() {
		Optional<ClaimedJob> job;
		while ((job = jobQueue.claimNext(lease)).isPresent()) {
			runExecutor.execute(job.get());
		}
	}
}
