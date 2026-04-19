package com.g2rain.iam.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类，用于全局配置 CORS（跨域资源共享）。
 * <p>
 * 该配置允许指定路径的跨域请求访问，并设置允许的请求方法、请求头及是否允许携带 Cookie。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 当前配置允许：
 * // - 所有路径的跨域请求
 * // - 所有来源域名
 * // - GET 和 POST 请求
 * // - 所有请求头
 * // - 允许携带 Cookie
 * }</pre>
 * </p>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>允许所有域名和请求头可能存在安全风险，请根据实际生产环境限制域名。</li>
 *     <li>allowCredentials(true) 允许 Cookie 跨域，需要前端 fetch 或 axios 配置 withCredentials: true。</li>
 * </ul>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置全局 CORS 策略。
     * <p>
     * 本方法重写自 {@link WebMvcConfigurer#addCorsMappings(CorsRegistry)}。
     * </p>
     *
     * @param registry {@link CorsRegistry} 实例，用于注册 CORS 映射规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")          // 所有路径
            .allowedOriginPatterns("*") // 允许所有域
            .allowedMethods("GET", "POST") // 允许 GET 和 POST 请求
            .allowedHeaders("*")        // 允许所有请求头
            .allowCredentials(true);    // 允许携带 Cookie
    }

    /**
     * 添加拦截器，处理 favicon.ico 请求，避免抛出异常。
     * <p>
     * 当访问 favicon.ico 时，直接返回 204 No Content，不进行后续处理。
     * </p>
     *
     * @param registry {@link InterceptorRegistry} 实例，用于注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
                if ("/favicon.ico".equals(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return false; // 阻止继续处理
                }
                return true;
            }
        }).addPathPatterns("/favicon.ico");
    }
}
