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

/** The log of one step inside a run: what went in, what came out, how it ended. */
@Entity
@Table(name = "step_runs")
public class StepRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(name = "step_key", nullable = false, updatable = false)
	private String stepKey;

	@Column(nullable = false, updatable = false)
	private int position;

	@Column(name = "connector_key", nullable = false, updatable = false)
	private String connectorKey;

	@Column(name = "action_key", nullable = false, updatable = false)
	private String actionKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StepStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> input;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> output;

	private String error;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected StepRun() {
	}

	public StepRun(UUID runId, String stepKey, int position, String connectorKey, String actionKey) {
		this.runId = runId;
		this.stepKey = stepKey;
		this.position = position;
		this.connectorKey = connectorKey;
		this.actionKey = actionKey;
		this.status = StepStatus.PENDING;
	}

	public void start(Map<String, Object> input, Instant now) {
		this.status = StepStatus.RUNNING;
		this.input = input;
		this.startedAt = now;
	}

	public void finish(StepStatus finalStatus, Map<String, Object> output, String error, Instant now) {
		this.status = finalStatus;
		this.output = output;
		this.error = error;
		this.finishedAt = now;
	}

	public void skip() {
		this.status = StepStatus.SKIPPED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public String getStepKey() {
		return stepKey;
	}

	public int getPosition() {
		return position;
	}

	public String getConnectorKey() {
		return connectorKey;
	}

	public String getActionKey() {
		return actionKey;
	}

	public StepStatus getStatus() {
		return status;
	}

	public Map<String, Object> getInput() {
		return input;
	}

	public Map<String, Object> getOutput() {
		return output;
	}

	public String getError() {
		return error;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
