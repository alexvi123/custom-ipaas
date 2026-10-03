package io.github.alexvi123.customipaas.execution;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work queue, stored in the {@code jobs} table. Plain SQL on purpose: {@code FOR UPDATE SKIP LOCKED} lets
 * several workers claim jobs at the same time without ever getting the same one.
 */
@Component
public class JobQueue {

	private final JdbcClient jdbc;

	public JobQueue(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Must be called inside the transaction that creates the run. */
	public void enqueue(UUID runId) {
		jdbc.sql("INSERT INTO jobs (run_id) VALUES (:runId)").param("runId", runId).update();
	}

	/** Takes the oldest available job and leases it, so no other worker picks it up until the lease expires. */
	@Transactional
	public Optional<ClaimedJob> claimNext(Duration lease) {
		Optional<ClaimedJob> job = jdbc.sql("""
				SELECT id, run_id FROM jobs
				WHERE run_at <= now() AND (locked_until IS NULL OR locked_until < now())
				ORDER BY run_at
				LIMIT 1
				FOR UPDATE SKIP LOCKED
				""")
				.query((rs, rowNum) -> new ClaimedJob(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class)))
				.optional();

		job.ifPresent(claimed -> jdbc.sql("""
				UPDATE jobs SET locked_until = now() + (:seconds * interval '1 second'), attempts = attempts + 1
				WHERE id = :id
				""")
				.param("seconds", lease.toSeconds())
				.param("id", claimed.id())
				.update());
		return job;
	}

	public void delete(UUID jobId) {
		jdbc.sql("DELETE FROM jobs WHERE id = :id").param("id", jobId).update();
	}
}
