package io.github.alexvi123.customipaas.execution;

import java.util.UUID;

public record ClaimedJob(UUID id, UUID runId) {
}
