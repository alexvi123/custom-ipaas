package io.github.alexvi123.customipaas.execution;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work queue, stored in the {@code jobs} table. Plain SQL on purpose: {@code FOR UPDATE SKIP LOCKED} lets
 * several workers claim jobs at the same time without ever getting the same one. Every change after the claim
 * checks {@code attempts}, so a worker that lost its lease can't touch the job any more.
 */
@Component
public class JobQueue {

	private final JdbcClient jdbc;

	public JobQueue(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Must be called inside the transaction that creates (or re-queues) the run. */
	public void enqueue(UUID runId) {
		jdbc.sql("INSERT INTO jobs (run_id) VALUES (:runId)").param("runId", runId).update();
	}

	/** Takes the oldest available job and leases it, so no other worker picks it up until the lease expires. */
	@Transactional
	public Optional<ClaimedJob> claimNext(Duration lease) {
		Optional<UUID> jobId = jdbc.sql("""
				SELECT id FROM jobs
				WHERE run_at <= now() AND (locked_until IS NULL OR locked_until < now())
				ORDER BY run_at
				LIMIT 1
				FOR UPDATE SKIP LOCKED
				""")
				.query(UUID.class)
				.optional();

		return jobId.map(id -> jdbc.sql("""
				UPDATE jobs SET locked_until = now() + (:seconds * interval '1 second'), attempts = attempts + 1
				WHERE id = :id
				RETURNING id, run_id, attempts
				""")
				.param("seconds", lease.toSeconds())
				.param("id", id)
				.query((rs, rowNum) -> new ClaimedJob(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class),
						rs.getInt("attempts")))
				.single());
	}

	/** Returns false when another worker has taken the job over. */
	public boolean extendLease(ClaimedJob job, Duration lease) {
		return jdbc.sql("""
				UPDATE jobs SET locked_until = now() + (:seconds * interval '1 second')
				WHERE id = :id AND attempts = :attempts
				""")
				.param("seconds", lease.toSeconds())
				.param("id", job.id())
				.param("attempts", job.attempts())
				.update() == 1;
	}

	/** Releases the job and makes it claimable again at {@code runAt} (a retry later). */
	public void reschedule(ClaimedJob job, Instant runAt) {
		jdbc.sql("UPDATE jobs SET run_at = :runAt, locked_until = NULL WHERE id = :id AND attempts = :attempts")
				.param("runAt", Timestamp.from(runAt))
				.param("id", job.id())
				.param("attempts", job.attempts())
				.update();
	}

	public void delete(ClaimedJob job) {
		jdbc.sql("DELETE FROM jobs WHERE id = :id AND attempts = :attempts")
				.param("id", job.id())
				.param("attempts", job.attempts())
				.update();
	}
}
