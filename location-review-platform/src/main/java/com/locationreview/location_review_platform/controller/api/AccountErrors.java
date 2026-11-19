package com.locationreview.location_review_platform.controller.api;

import com.locationreview.location_review_platform.service.AccountException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = AccountApiController.class)
public class AccountErrors {
    @ExceptionHandler(AccountException.class)
    ProblemDetail account(AccountException exception, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setInstance(URI.create(request.getRequestURI())); problem.setProperty("code", exception.code());
        if (!exception.fieldErrors().isEmpty()) problem.setProperty("fieldErrors", exception.fieldErrors());
        return problem;
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception exception, jakarta.servlet.http.HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request.");
        problem.setInstance(URI.create(request.getRequestURI())); problem.setProperty("code", "invalid_request"); return problem;
    }
}
