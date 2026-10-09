package com.company.officecommute.global.exception;

import com.company.officecommute.auth.AuthenticationFailedException;
import com.company.officecommute.auth.CsrfOriginRejectedException;
import com.company.officecommute.auth.ForbiddenException;
import com.company.officecommute.domain.annual_leave.AnnualLeaveCriteriaNotMetException;
import com.company.officecommute.domain.annual_leave.AnnualLeaveDuplicateException;
import com.company.officecommute.domain.annual_leave.AnnualLeavePastDateException;
import com.company.officecommute.domain.annual_leave.EmployeeWithoutTeamException;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteEndWindowExpiredException;
import com.company.officecommute.domain.commute.CommuteNotStartedException;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.commute.InvalidCommuteRangeException;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.employee.EmployeeAlreadyExistsException;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.employee.InvalidRetirementDateException;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.domain.team.TeamNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            NullPointerException.class
    })
    public ErrorResult handleUnexpectedDomainViolation(RuntimeException e) {
        log.error("Unexpected domain violation — likely a bug. Domain invariant or guard fired on a path that should not be reachable from user input.", e);
        return new ErrorResult("UNEXPECTED_DOMAIN_VIOLATION", "내부 도메인 검증에 실패했습니다.");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResult handleSystemError(Exception e) {
        log.error("Unexpected system error", e);
        return new ErrorResult("INTERNAL_SERVER_ERROR", "내부 서버 오류가 발생했습니다");
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public ErrorResult handleNoResourceFound(NoResourceFoundException e) {
        log.warn("No resource for path: {}", e.getResourcePath());
        return new ErrorResult("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ValidationErrorResult handleValidation(MethodArgumentNotValidException e) {
        log.warn("Validation failed for request", e);
        List<FieldErrorResult> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResult(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ValidationErrorResult("VALIDATION_ERROR", "입력값이 올바르지 않습니다", errors);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResult handleInvalidJson(HttpMessageNotReadableException e) {
        String message = invalidJsonMessage(e);
        String causeType = e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName();
        log.warn("Invalid JSON request: {} (cause: {})", message, causeType);
        return new ErrorResult("INVALID_JSON", message);
    }

    private String invalidJsonMessage(HttpMessageNotReadableException e) {
        if (!(e.getCause() instanceof InvalidFormatException invalidFormatException)) {
            return "요청 본문을 해석할 수 없습니다.";
        }

        String fieldPath = fieldPath(invalidFormatException);
        if (fieldPath.isBlank()) {
            return "요청 본문을 해석할 수 없습니다.";
        }
        return "필드 %s의 값이 올바르지 않습니다.".formatted(fieldPath);
    }

    private String fieldPath(InvalidFormatException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationFailedException.class)
    public ErrorResult handleAuthenticationFailed(AuthenticationFailedException e) {
        log.warn("Authentication failed: {}", e.getMessage());
        return new ErrorResult("UNAUTHORIZED", e.getMessage());
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ErrorResult handleForbidden(ForbiddenException e) {
        log.warn("Access denied: {}", e.getMessage());
        return new ErrorResult("FORBIDDEN", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(TeamAlreadyExistsException.class)
    public ErrorResult handleTeamAlreadyExists(TeamAlreadyExistsException e) {
        log.warn("Team already exists: {}", e.getMessage());
        return new ErrorResult("TEAM_ALREADY_EXISTS", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(EmployeeAlreadyExistsException.class)
    public ErrorResult handleEmployeeAlreadyExists(EmployeeAlreadyExistsException e) {
        log.warn("Employee already exists: {}", e.getMessage());
        return new ErrorResult("EMPLOYEE_ALREADY_EXISTS", e.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ErrorResult handleEmployeeNotFound(EmployeeNotFoundException e) {
        log.warn("Employee not found: {}", e.getMessage());
        return new ErrorResult("EMPLOYEE_NOT_FOUND", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidRetirementDateException.class)
    public ErrorResult handleInvalidRetirementDate(InvalidRetirementDateException e) {
        log.warn("Invalid retirement date: {}", e.getMessage());
        return new ErrorResult("INVALID_RETIREMENT_DATE", e.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(TeamNotFoundException.class)
    public ErrorResult handleTeamNotFound(TeamNotFoundException e) {
        log.warn("Team not found: {}", e.getMessage());
        return new ErrorResult("TEAM_NOT_FOUND", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(CommuteNotStartedException.class)
    public ErrorResult handleCommuteNotStarted(CommuteNotStartedException e) {
        log.warn("Commute not started: {}", e.getMessage());
        return new ErrorResult("COMMUTE_NOT_STARTED", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(CommuteAlreadyEndedException.class)
    public ErrorResult handleCommuteAlreadyEnded(CommuteAlreadyEndedException e) {
        log.warn("Commute already ended: {}", e.getMessage());
        return new ErrorResult("COMMUTE_ALREADY_ENDED", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidCommuteRangeException.class)
    public ErrorResult handleInvalidCommuteRange(InvalidCommuteRangeException e) {
        log.warn("Invalid commute range: {}", e.getMessage());
        return new ErrorResult("INVALID_COMMUTE_RANGE", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(CommuteEndWindowExpiredException.class)
    public ErrorResult handleCommuteEndWindowExpired(CommuteEndWindowExpiredException e) {
        log.warn("Commute end window expired: {}", e.getMessage());
        return new ErrorResult("COMMUTE_END_WINDOW_EXPIRED", e.getMessage());
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(CsrfOriginRejectedException.class)
    public ErrorResult handleCsrfOriginRejected(CsrfOriginRejectedException e) {
        log.warn("CSRF origin rejected: {}", e.getMessage());
        return new ErrorResult("CSRF_ORIGIN_REJECTED", e.getMessage());
    }

    @ExceptionHandler(CorrectionException.class)
    public ResponseEntity<ErrorResult> handleCorrection(CorrectionException e) {
        log.warn("Commute correction rejected: {} {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode().getHttpStatus())
                .body(new ErrorResult(e.getCode().name(), e.getMessage()));
    }

    @ExceptionHandler(ClosingException.class)
    public ResponseEntity<ErrorResult> handleClosing(ClosingException e) {
        log.warn("Closing rule rejected: {} {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode().getHttpStatus())
                .body(new ErrorResult(e.getCode().name(), e.getMessage()));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(EmployeeWithoutTeamException.class)
    public ErrorResult handleEmployeeWithoutTeam(EmployeeWithoutTeamException e) {
        log.warn("Employee without team: {}", e.getMessage());
        return new ErrorResult("EMPLOYEE_WITHOUT_TEAM", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(AnnualLeaveCriteriaNotMetException.class)
    public ErrorResult handleAnnualLeaveCriteriaNotMet(AnnualLeaveCriteriaNotMetException e) {
        log.warn("Annual leave criteria not met: {}", e.getMessage());
        return new ErrorResult("ANNUAL_LEAVE_CRITERIA_NOT_MET", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(AnnualLeaveDuplicateException.class)
    public ErrorResult handleAnnualLeaveDuplicate(AnnualLeaveDuplicateException e) {
        log.warn("Annual leave duplicate: {}", e.getMessage());
        return new ErrorResult("ANNUAL_LEAVE_DUPLICATE", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(AnnualLeavePastDateException.class)
    public ErrorResult handleAnnualLeavePastDate(AnnualLeavePastDateException e) {
        log.warn("Annual leave past date: {}", e.getMessage());
        return new ErrorResult("ANNUAL_LEAVE_PAST_DATE", e.getMessage());
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(HolidayDataUnavailableException.class)
    public ErrorResult handleHolidayDataUnavailable(HolidayDataUnavailableException e) {
        log.warn("Holiday data unavailable: {}", e.getMessage());
        return new ErrorResult("HOLIDAY_DATA_UNAVAILABLE", e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateWorkOnDateException.class)
    public ErrorResult handleDuplicateWorkOnDate(DuplicateWorkOnDateException e) {
        log.warn("Duplicate work on date: {}", e.getMessage());
        return new ErrorResult("DUPLICATE_WORK", e.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ErrorResult handleDataIntegrity(DataIntegrityViolationException e) {
        Throwable rootCause = e.getRootCause();
        String detail = (rootCause != null) ? rootCause.getMessage() : e.getMessage();
        log.error("Unhandled data constraint violation — likely missing domain pre-check: {}", detail, e);
        return new ErrorResult("DATA_INTEGRITY_ERROR", "데이터 제약조건을 위반했습니다");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ErrorResult handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter: {}", e.getMessage());
        return new ErrorResult("MISSING_PARAMETER", "필수 파라미터가 누락되었습니다: " + e.getParameterName());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResult handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Parameter type mismatch: {}", e.getMessage());
        return new ErrorResult("INVALID_PARAMETER", "파라미터 형식이 올바르지 않습니다: " + e.getName());
    }
}
