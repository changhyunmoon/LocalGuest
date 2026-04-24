package com.team6.apiserver.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class HttpHandlerLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpHandlerLoggingInterceptor.class);
    static final String ATTR_START_NANOS = HttpHandlerLoggingInterceptor.class.getName() + ".startNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        request.setAttribute(ATTR_START_NANOS, System.nanoTime());
        String javaMethod = hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName();
        log.info("[API] Enter - method: {}, requestUri: {}, httpMethod: {}", javaMethod, request.getRequestURI(), request.getMethod());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod hm)) {
            return;
        }
        Object start = request.getAttribute(ATTR_START_NANOS);
        if (!(start instanceof Long startNanos)) {
            return;
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String javaMethod = hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName();
        log.info("[API] Exit - method: {}, latencyMs: {}, statusCode: {}, requestUri: {}",
                javaMethod,
                latencyMs,
                response.getStatus(),
                request.getRequestURI());
    }
}
