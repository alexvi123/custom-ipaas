package io.github.alexvi123.customipaas.trigger;

public class WebhookNotFoundException extends RuntimeException {

	public WebhookNotFoundException() {
		super("Unknown webhook");
	}
}
