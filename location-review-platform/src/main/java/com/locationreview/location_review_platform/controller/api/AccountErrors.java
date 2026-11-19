package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.service.AccountException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {AccountApiController.class, ReviewApiController.class, CollectionApiController.class,
        BookmarkApiController.class, PeopleApiController.class, ReviewInteractionApiController.class})
public class AccountErrors {
    @ExceptionHandler(AccountException.class)
    ProblemDetail account(AccountException exception, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI())); problem.setProperty("code", exception.code());
        if (!exception.fieldErrors().isEmpty()) problem.setProperty("fieldErrors", exception.fieldErrors());
        return problem;
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ProblemDetail validation(org.springframework.web.bind.MethodArgumentNotValidException exception, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the request fields.");
        problem.setInstance(URI.create(request.getRequestURI())); problem.setProperty("code", "invalid_fields");
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", errors); return problem;
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception exception, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request.");
        problem.setInstance(URI.create(request.getRequestURI())); problem.setProperty("code", "invalid_request"); return problem;
    }
}
