package io.github.alexvi123.customipaas.api;

import io.github.alexvi123.customipaas.connector.ActionNotFoundException;
import io.github.alexvi123.customipaas.connector.ConnectorException;
import io.github.alexvi123.customipaas.connector.ConnectorNotFoundException;
import io.github.alexvi123.customipaas.connector.FieldNotFoundException;
import io.github.alexvi123.customipaas.connector.InvalidInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ConnectorNotFoundException.class, ActionNotFoundException.class, FieldNotFoundException.class})
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

	@ExceptionHandler(ConnectorException.class)
	ProblemDetail connectorFailure(ConnectorException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
		problem.setTitle("Connector error");
		return problem;
	}
}
