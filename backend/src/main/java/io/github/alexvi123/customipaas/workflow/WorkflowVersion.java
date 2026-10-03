package io.github.alexvi123.customipaas.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_versions")
public class WorkflowVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workflow_id", nullable = false, updatable = false)
	private UUID workflowId;

	@Column(nullable = false, updatable = false)
	private int version;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private Map<String, Object> definition;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WorkflowVersion() {
	}

	public WorkflowVersion(UUID workflowId, int version, Map<String, Object> definition, Instant now) {
		this.workflowId = workflowId;
		this.version = version;
		this.definition = definition;
		this.createdAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowId() {
		return workflowId;
	}

	public int getVersion() {
		return version;
	}

	public Map<String, Object> getDefinition() {
		return definition;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
