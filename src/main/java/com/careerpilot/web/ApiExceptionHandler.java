package com.careerpilot.web;

import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.rag.KnowledgeException;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final tools.jackson.databind.json.JsonMapper ERROR_JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    @ExceptionHandler(KnowledgeException.class)
    public ResponseEntity<ApiError> knowledgeError(KnowledgeException exception) {
        log.warn("Knowledge or AI request failed: {}", exception.getClass().getSimpleName());
        return error(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> missingFile(MissingServletRequestPartException exception) {
        return error(HttpStatus.BAD_REQUEST, "缺少上传文件 file");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> fileTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "上传文件超过 10MB 限制");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> missingRoute(Exception exception) {
        return error(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(Exception exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(Exception exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> statusError(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(status, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason());
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<ApiError> unauthorized(Exception exception) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "请求参数无效，请检查格式、长度和允许的取值");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "请求体格式错误或缺少必填字段");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> databaseUnavailable(DataAccessException exception) {
        log.error("Database request failed: {}", exception.getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "数据库访问失败，请检查连接配置或稍后重试");
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiError> toolError(ToolExecutionException exception) {
        log.warn("Tool call failed: {}", exception.getClass().getSimpleName());
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceNotFoundException notFound) {
                return error(HttpStatus.NOT_FOUND, notFound.getMessage());
            }
            if (cause instanceof DataAccessException) {
                return error(HttpStatus.SERVICE_UNAVAILABLE, "数据库访问失败，请检查连接配置或稍后重试");
            }
            if (cause instanceof IllegalArgumentException invalid) {
                return error(HttpStatus.BAD_REQUEST, "工具参数无效，请检查格式、长度和允许的取值");
            }
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "工具执行失败");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled request failed: {}", exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理请求，请稍后重试");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, org.slf4j.MDC.get("requestId")));
    }

    public static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        String requestId = response.getHeader("X-Request-Id");
        response.getWriter().write(ERROR_JSON
                .writeValueAsString(new ApiError(status, message, requestId)));
    }

    public record ApiError(int status, String message, String requestId) {
    }
}
