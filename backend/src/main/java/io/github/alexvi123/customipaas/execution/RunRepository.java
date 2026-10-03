package io.github.alexvi123.customipaas.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, UUID> {

	List<Run> findByWorkflowIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID workflowId, Instant before, Limit limit);
}
