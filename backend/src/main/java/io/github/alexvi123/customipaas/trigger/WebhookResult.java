package io.github.alexvi123.customipaas.trigger;

import java.util.UUID;

/** The run a webhook delivery maps to; {@code duplicate} when an earlier delivery with the same key created it. */
public record WebhookResult(UUID runId, boolean duplicate) {
}
