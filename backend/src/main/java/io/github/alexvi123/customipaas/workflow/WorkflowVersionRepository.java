package io.github.alexvi123.customipaas.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

	Optional<WorkflowVersion> findByWorkflowIdAndVersion(UUID workflowId, int version);
}
