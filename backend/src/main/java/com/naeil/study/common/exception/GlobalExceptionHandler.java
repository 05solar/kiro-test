package com.naeil.study.common.exception;

import com.naeil.study.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기.
 *
 * <p>컨트롤러에서 발생한 예외를 {@link ErrorResponse} 한 가지 형식으로 변환한다.
 * 예상하지 못한 예외의 내부 메시지는 응답에 노출하지 않고 로그로만 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.info("business exception: code={}, message={}", errorCode.name(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    /**
     * 요청 본문 검증 실패. 첫 번째 필드 오류 메시지를 그대로 사용자에게 보여준다.
     * 응답 형식은 다른 에러와 동일하게 유지한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(errorCode.getDefaultMessage());
        log.info("request validation failed: {}", message);
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        log.info("constraint violation: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 본문이 비었거나 JSON 형식/타입이 잘못된 경우. 서버 오류가 아니므로 400으로 응답한다.
     * 파싱 실패 원문에는 요청 내용이 그대로 담기므로 응답에 노출하지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.info("malformed request body: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 경로 변수 타입 변환 실패(예: documentId가 UUID 형식이 아님).
     * 서버 오류가 아니므로 400으로 응답한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.info("path variable type mismatch: name={}", e.getName());
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * multipart 요청에 files 파트가 없는 경우.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.info("missing request part: {}", e.getRequestPartName());
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 서블릿 컨테이너가 먼저 잘라낸 대용량 업로드.
     *
     * <p>애플리케이션 검증까지 오지 못하므로 여기서 같은 에러 코드로 맞춰 준다.
     * 그렇지 않으면 20MB 초과 파일이 500으로 나간다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.info("upload size exceeded at container level: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.FILE_SIZE_EXCEEDED;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 매핑되지 않은 주소.
     *
     * <p>이 처리가 없으면 오타 난 주소나 스캐너의 탐색 요청이 전부 500으로 나간다.
     * 서버가 고장 난 것이 아니라 그런 주소가 없는 것이므로 404가 맞다.
     *
     * <p>500으로 두면 모니터링이 오탐으로 시끄러워져, 정작 진짜 장애를 놓친다.
     * 로그도 error 가 아니라 debug 로 남긴다. 존재하지 않는 주소를 찾는 요청은
     * 인터넷에 붙는 순간부터 끊임없이 들어온다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("no handler for path: {}", e.getResourcePath());
        ErrorCode errorCode = ErrorCode.ENDPOINT_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /** 주소는 있는데 메서드가 다른 경우. 역시 서버 오류가 아니다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.debug("method not supported: {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("unexpected exception", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
