package io.github.alexvi123.customipaas.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.alexvi123.customipaas.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "ipaas.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class JobQueueIT {

	@Autowired
	private JobQueue jobQueue;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate transactions;

	@BeforeEach
	void cleanQueue() {
		jdbc.sql("DELETE FROM workflows").update(); // cascades to versions, runs and jobs
	}

	private UUID insertRunWithJob() {
		UUID workflowId = jdbc.sql("""
				INSERT INTO workflows (name, current_version, webhook_token) VALUES ('w', 1, :token) RETURNING id
				""").param("token", UUID.randomUUID().toString()).query(UUID.class).single();
		UUID versionId = jdbc.sql("""
				INSERT INTO workflow_versions (workflow_id, version, definition) VALUES (:w, 1, '{}') RETURNING id
				""").param("w", workflowId).query(UUID.class).single();
		UUID runId = jdbc.sql("""
				INSERT INTO runs (workflow_id, workflow_version_id, status, trigger_payload)
				VALUES (:w, :v, 'PENDING', '{}') RETURNING id
				""").param("w", workflowId).param("v", versionId).query(UUID.class).single();
		transactions.executeWithoutResult(tx -> jobQueue.enqueue(runId));
		return runId;
	}

	@Test
	void twoWorkersClaimingAtOnceNeverGetTheSameJob() throws Exception {
		insertRunWithJob();
		insertRunWithJob();
		CountDownLatch bothInside = new CountDownLatch(2);

		// Each "worker" claims inside a transaction and holds it open until the other has claimed too.
		Callable<Optional<ClaimedJob>> worker = () -> transactions.execute(tx -> {
			Optional<ClaimedJob> claimed = jobQueue.claimNext(Duration.ofMinutes(5));
			bothInside.countDown();
			try {
				bothInside.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return claimed;
		});

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Optional<ClaimedJob>>> results = pool.invokeAll(List.of(worker, worker));
			ClaimedJob first = results.get(0).get().orElseThrow();
			ClaimedJob second = results.get(1).get().orElseThrow();
			assertThat(first.id()).isNotEqualTo(second.id());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aLeasedJobIsNotClaimedAgainUntilTheLeaseExpires() {
		UUID runId = insertRunWithJob();

		Optional<ClaimedJob> first = jobQueue.claimNext(Duration.ofMinutes(5));
		Optional<ClaimedJob> second = jobQueue.claimNext(Duration.ofMinutes(5));

		assertThat(first).map(ClaimedJob::runId).contains(runId);
		assertThat(second).isEmpty();

		jdbc.sql("UPDATE jobs SET locked_until = now() - interval '1 second'").update(); // lease expired
		assertThat(jobQueue.claimNext(Duration.ofMinutes(5))).map(ClaimedJob::runId).contains(runId);
		assertThat(jdbc.sql("SELECT attempts FROM jobs").query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void deletedJobsAreGone() {
		insertRunWithJob();
		ClaimedJob job = jobQueue.claimNext(Duration.ofMinutes(5)).orElseThrow();

		jobQueue.delete(job.id());

		assertThat(jdbc.sql("SELECT count(*) FROM jobs").query(Long.class).single()).isZero();
	}
}
