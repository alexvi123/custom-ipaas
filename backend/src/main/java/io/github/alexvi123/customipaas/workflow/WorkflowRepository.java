package io.github.alexvi123.customipaas.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

	Optional<Workflow> findByWebhookToken(String webhookToken);

	List<Workflow> findAllByOrderByCreatedAtDesc();
}
