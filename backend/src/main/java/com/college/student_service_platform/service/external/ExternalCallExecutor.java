package com.college.student_service_platform.service.external;

import com.college.student_service_platform.common.ExternalServiceException;
import com.college.student_service_platform.config.ExternalServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

@Component
public class ExternalCallExecutor {
    private static final Logger log = LoggerFactory.getLogger(ExternalCallExecutor.class);
    private final ExternalServiceProperties properties;

    public ExternalCallExecutor(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    public <T> T executeRetryable(String operation, Supplier<T> action) {
        RuntimeException lastFailure = null;
        int maxAttempts = properties.getHttp().getMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                log.warn("External call {} failed on attempt {}/{}", operation, attempt, maxAttempts);
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                }
            } catch (RuntimeException exception) {
                throw new ExternalServiceException(operation + "调用失败", exception);
            }
        }
        throw new ExternalServiceException(operation + "超时或不可用", lastFailure);
    }

    public <T> T executeOnce(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(operation + "调用失败", exception);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(500L * attempt, 1_500L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("外部调用重试被中断", exception);
        }
    }
}
