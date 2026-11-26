package com.aitraining.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器 + 跨域配置
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/captcha",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/favicon.ico",
                        "/error"
                );
    }

    /**
     * 跨域策略：只放开本机来源。
     *
     * <p>原来写的是 allowedOriginPatterns("*") + allowCredentials(true)，
     * 实测任意 Origin 都会被原样回显且带 Allow-Credentials: true。
     * 本项目令牌走 Authorization 头而非 Cookie，实际无法被第三方站点冒用，
     * 但这种配置一旦将来引入 Cookie 会话就会直接变成 CSRF 通道，故收敛为白名单。
     * 答辩演示固定在本机 3000 端口，localhost 与 127.0.0.1 任意端口都放行。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
