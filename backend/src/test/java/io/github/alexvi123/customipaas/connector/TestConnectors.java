package io.github.alexvi123.customipaas.connector;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class TestConnectors {

	private TestConnectors() {
	}

	static Connector named(String key) {
		return new FakeConnector(key, List.of(), input -> ActionResult.success(Map.of()));
	}

	static FakeConnector echo(Function<ActionInput, ActionResult> behaviour) {
		ActionDefinition action = new ActionDefinition("echo", "Echo", "Returns its input",
				List.of(FieldDefinition.string("message", "Message").asRequired(),
						FieldDefinition.select("target", "Target").withDynamicOptions()));
		return new FakeConnector("fake", List.of(action), behaviour);
	}

	static final class FakeConnector implements Connector {

		private final String key;
		private final List<ActionDefinition> actions;
		private final Function<ActionInput, ActionResult> behaviour;
		int executions;
		OptionsContext lastOptionsContext;

		FakeConnector(String key, List<ActionDefinition> actions, Function<ActionInput, ActionResult> behaviour) {
			this.key = key;
			this.actions = actions;
			this.behaviour = behaviour;
		}

		@Override
		public ConnectorInfo info() {
			return new ConnectorInfo(key, key + " name", key + " description");
		}

		@Override
		public List<ActionDefinition> actions() {
			return actions;
		}

		@Override
		public List<Option> dynamicOptions(String actionKey, String fieldKey, OptionsContext context) {
			lastOptionsContext = context;
			return List.of(new Option("a", "Option A"));
		}

		@Override
		public ActionResult execute(String actionKey, ActionInput input, ExecutionContext context) {
			executions++;
			return behaviour.apply(input);
		}
	}
}
