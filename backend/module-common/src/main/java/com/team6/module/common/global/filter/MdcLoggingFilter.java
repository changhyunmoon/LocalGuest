package com.team6.module.common.global.filter;

import jakarta.servlet.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.util.UUID;

@Component
public class MdcLoggingFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException{
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        //1. 요청이 들어오면 고유한 8자리 글자 Trace ID를 생성
        String traceId = UUID.randomUUID().toString().substring(0,8);
        //2. MDC 라는 곳에 해당 아이디를 보관
        MDC.put("traceId", traceId);
        try{
            //3. Controller에 요청을 넘김
            chain.doFilter(request,response);
        }finally {
            //요청 처리가 끝나면 반드시 비워줘야 한다.
            MDC.clear();
        }
    }

    @Override
    public void destroy(){
        Filter.super.destroy();
    }
}
