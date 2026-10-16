package com.chessapp.common.exception;

import com.chessapp.common.dto.FieldValidationError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * @author pari on 10/16/26
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<FieldValidationError> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                "One or more request fields are invalid"
        );

        problem.setTitle("Request validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(
                exception,
                problem,
                headers,
                status,
                request
        );
    }
}

/**
 * Example response:
 * {
 *   "type": "about:blank",
 *   "title": "Request validation failed",
 *   "status": 400,
 *   "detail": "One or more request fields are invalid",
 *   "instance": "/api/v1/auth/register",
 *   "code": "VALIDATION_FAILED",
 *   "errors": [
 *     {
 *       "field": "email",
 *       "message": "must be a well-formed email address"
 *     }
 *   ]
 * }
 * */