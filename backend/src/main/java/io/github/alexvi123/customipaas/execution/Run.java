package io.github.alexvi123.customipaas.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One execution of a workflow, pinned to the workflow version it ran. */
@Entity
@Table(name = "runs")
public class Run {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workflow_id", nullable = false, updatable = false)
	private UUID workflowId;

	@Column(name = "workflow_version_id", nullable = false, updatable = false)
	private UUID workflowVersionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RunStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "trigger_payload", nullable = false, updatable = false)
	private Map<String, Object> triggerPayload;

	private String error;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected Run() {
	}

	public Run(UUID workflowId, UUID workflowVersionId, Map<String, Object> triggerPayload, Instant now) {
		this.workflowId = workflowId;
		this.workflowVersionId = workflowVersionId;
		this.triggerPayload = triggerPayload;
		this.status = RunStatus.PENDING;
		this.createdAt = now;
	}

	public void start(Instant now) {
		this.status = RunStatus.RUNNING;
		this.startedAt = now;
	}

	public void finish(RunStatus finalStatus, String error, Instant now) {
		this.status = finalStatus;
		this.error = error;
		this.finishedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowId() {
		return workflowId;
	}

	public UUID getWorkflowVersionId() {
		return workflowVersionId;
	}

	public RunStatus getStatus() {
		return status;
	}

	public Map<String, Object> getTriggerPayload() {
		return triggerPayload;
	}

	public String getError() {
		return error;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
