package io.github.alexvi123.customipaas.execution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRunRepository extends JpaRepository<StepRun, UUID> {

	List<StepRun> findByRunIdOrderByPosition(UUID runId);
}
