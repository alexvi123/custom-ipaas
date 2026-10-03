package io.github.alexvi123.customipaas.trigger;

import org.springframework.http.HttpStatus;

/** The webhook body was rejected: too large (413) or not valid JSON (400). */
public class InvalidPayloadException extends RuntimeException {

	private final HttpStatus status;

	public InvalidPayloadException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
