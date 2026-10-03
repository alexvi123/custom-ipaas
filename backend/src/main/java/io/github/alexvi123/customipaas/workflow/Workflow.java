package io.github.alexvi123.customipaas.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows")
public class Workflow {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "current_version", nullable = false)
	private int currentVersion;

	@Column(name = "webhook_token", nullable = false, unique = true, updatable = false)
	private String webhookToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Workflow() {
	}

	public Workflow(String name, String webhookToken, Instant now) {
		this.name = name;
		this.webhookToken = webhookToken;
		this.currentVersion = 1;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Renames the workflow and moves it to the next version number; returns that number. */
	public int nextVersion(String newName, Instant now) {
		this.name = newName;
		this.currentVersion++;
		this.updatedAt = now;
		return currentVersion;
	}

	public void setActive(boolean active, Instant now) {
		this.active = active;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public int getCurrentVersion() {
		return currentVersion;
	}

	public String getWebhookToken() {
		return webhookToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
