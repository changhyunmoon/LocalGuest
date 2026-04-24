package com.team6.apiserver.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class RepositoryLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(RepositoryLoggingAspect.class);

    @Around("execution(public * com.team6..*Repository+.*(..))")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("[DB] Completed - method: {}, latencyMs: {}, status: success", method, latencyMs);
            return result;
        } catch (Throwable ex) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn("[DB] Failed - method: {}, latencyMs: {}, status: failed, cause: {}", method, latencyMs, ex.getMessage());
            throw ex;
        }
    }
}
