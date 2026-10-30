package cn.pianke.exception;

import cn.pianke.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BizException error) {
        return ResponseEntity.status(error.getStatus())
                .body(new Result<>(error.getStatus().value(), error.getMessage(), null));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception error) {
        return ResponseEntity.badRequest().body(new Result<>(400, "请求参数不正确", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception error) {
        LOG.error("请求处理失败", error);
        return ResponseEntity.internalServerError().body(new Result<>(500, "服务暂时不可用，请稍后重试", null));
    }
}
