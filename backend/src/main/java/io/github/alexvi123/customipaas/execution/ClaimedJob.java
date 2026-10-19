package io.github.alexvi123.customipaas.execution;

import java.util.UUID;

/**
 * A job this worker holds. {@code attempts} is the claim number and acts as a fencing token: if another worker
 * re-claims the job after our lease expired, the number moves on and our updates to the job stop matching.
 */
public record ClaimedJob(UUID id, UUID runId, int attempts) {
}
