package com.team6.apiserver.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long startNanos = System.nanoTime();
        log.info("[HTTP] Request started - requestUri: {}, method: {}", request.getRequestURI(), request.getMethod());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info(
                    "[HTTP] Request completed - statusCode: {}, latencyMs: {}, requestUri: {}, method: {}",
                    response.getStatus(),
                    latencyMs,
                    request.getRequestURI(),
                    request.getMethod()
            );
            MDC.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String requestTraceId = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(requestTraceId)) {
            return requestTraceId;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
