package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.connector.ActionNotFoundException;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorNotFoundException;
import io.github.alexvi123.customipaas.connector.FieldNotFoundException;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import io.github.alexvi123.customipaas.execution.RunNotFoundException;
import io.github.alexvi123.customipaas.trigger.InvalidPayloadException;
import io.github.alexvi123.customipaas.trigger.WebhookNotFoundException;
import io.github.alexvi123.customipaas.trigger.WorkflowInactiveException;
import io.github.alexvi123.customipaas.workflow.InvalidWorkflowException;
import io.github.alexvi123.customipaas.workflow.WorkflowNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ConnectorNotFoundException.class, ActionNotFoundException.class, FieldNotFoundException.class,
			WorkflowNotFoundException.class, RunNotFoundException.class, WebhookNotFoundException.class})
	ProblemDetail notFound(RuntimeException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problem.setTitle("Not found");
		return problem;
	}

	@ExceptionHandler(InvalidInputException.class)
	ProblemDetail invalidInput(InvalidInputException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are invalid");
		problem.setTitle("Invalid input");
		problem.setProperty("errors", e.fieldErrors());
		return problem;
	}

	@ExceptionHandler(InvalidWorkflowException.class)
	ProblemDetail invalidWorkflow(InvalidWorkflowException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The workflow definition has problems");
		problem.setTitle("Invalid workflow");
		problem.setProperty("errors", e.errors());
		return problem;
	}

	@ExceptionHandler(WorkflowInactiveException.class)
	ProblemDetail inactive(WorkflowInactiveException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problem.setTitle("Workflow inactive");
		return problem;
	}

	@ExceptionHandler(InvalidPayloadException.class)
	ProblemDetail invalidPayload(InvalidPayloadException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
		problem.setTitle("Invalid webhook payload");
		return problem;
	}

	@ExceptionHandler(ConnectorException.class)
	ProblemDetail connectorFailure(ConnectorException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
		problem.setTitle("Connector error");
		return problem;
	}
}
