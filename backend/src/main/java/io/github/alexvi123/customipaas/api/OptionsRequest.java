package io.github.alexvi123.customipaas.api;

import java.util.Map;

/** Form values filled in so far, for options that depend on other fields. */
public record OptionsRequest(Map<String, Object> values) {

	public OptionsRequest {
		values = values == null ? Map.of() : values;
	}
}
