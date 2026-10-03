package io.github.alexvi123.customipaas.workflow;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final WorkflowRepository workflows;
	private final WorkflowVersionRepository versions;
	private final WorkflowDefinitionValidator validator;

	public WorkflowService(WorkflowRepository workflows, WorkflowVersionRepository versions,
			WorkflowDefinitionValidator validator) {
		this.workflows = workflows;
		this.versions = versions;
		this.validator = validator;
	}

	@Transactional(readOnly = true)
	public List<Workflow> list() {
		return workflows.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public WorkflowWithVersion get(UUID id) {
		Workflow workflow = find(id);
		return new WorkflowWithVersion(workflow, currentVersion(workflow));
	}

	@Transactional
	public WorkflowWithVersion create(String name, Map<String, Object> rawDefinition) {
		WorkflowDefinition definition = validator.validate(rawDefinition);
		Instant now = Instant.now();
		Workflow workflow = workflows.save(new Workflow(name, newWebhookToken(), now));
		WorkflowVersion version = versions.save(new WorkflowVersion(workflow.getId(), 1, definition.toMap(), now));
		return new WorkflowWithVersion(workflow, version);
	}

	/** Saving never changes an existing version: it creates the next one. */
	@Transactional
	public WorkflowWithVersion update(UUID id, String name, Map<String, Object> rawDefinition) {
		Workflow workflow = find(id);
		WorkflowDefinition definition = validator.validate(rawDefinition);
		Instant now = Instant.now();
		int versionNumber = workflow.nextVersion(name, now);
		WorkflowVersion version = versions.save(new WorkflowVersion(workflow.getId(), versionNumber, definition.toMap(), now));
		return new WorkflowWithVersion(workflow, version);
	}

	@Transactional
	public WorkflowWithVersion setActive(UUID id, boolean active) {
		Workflow workflow = find(id);
		workflow.setActive(active, Instant.now());
		return new WorkflowWithVersion(workflow, currentVersion(workflow));
	}

	@Transactional
	public void delete(UUID id) {
		workflows.delete(find(id));
	}

	private Workflow find(UUID id) {
		return workflows.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
	}

	private WorkflowVersion currentVersion(Workflow workflow) {
		return versions.findByWorkflowIdAndVersion(workflow.getId(), workflow.getCurrentVersion())
				.orElseThrow(() -> new IllegalStateException("Workflow " + workflow.getId() + " has no current version"));
	}

	private static String newWebhookToken() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
