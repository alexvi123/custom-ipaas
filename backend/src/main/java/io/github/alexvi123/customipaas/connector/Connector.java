package io.github.alexvi123.customipaas.connector;

import java.util.List;

/**
 * A plugin for one external app. Implementations are Spring {@code @Component}s; the registry finds them all.
 * The frontend never hardcodes a connector's fields: everything it renders comes from {@link #actions()}.
 */
public interface Connector {

	ConnectorInfo info();

	List<ActionDefinition> actions();

	/** Live values for a select field marked {@code dynamicOptions}. */
	default List<Option> dynamicOptions(String actionKey, String fieldKey, OptionsContext context) {
		throw new FieldNotFoundException(info().key(), actionKey, fieldKey);
	}

	ActionResult execute(String actionKey, ActionInput input, ExecutionContext context);
}
