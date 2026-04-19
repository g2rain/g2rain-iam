package com.g2rain.iam.controller;

import com.g2rain.common.utils.Strings;
import com.g2rain.iam.service.AuthService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 登录控制器，处理用户登录请求。
 * <p>
 * 该控制器处理登录请求，通过用户名和密码验证用户身份。如果验证通过，将创建会话并将会话 ID 设置为 HttpOnly Cookie，然后重定向到授权页面。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 POST 请求进行用户登录
 * POST /auth/login
 * Content-Type: application/x-www-form-urlencoded
 *
 * username=user123&password=secret&clientId=client123&redirectUri=http://example.com/callback&state=xyz
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth")
public class LoginController {

    /**
     * 认证服务，用于验证用户名和密码并创建会话。
     */
    private final AuthService authService;

    /**
     * ModelAndView 服务，用于处理视图重定向逻辑。
     */
    private final ModelAndViewService modelAndViewService;

    /**
     * 用户登录接口，接收用户名和密码进行身份验证。
     * <p>
     * 如果身份验证成功，会生成一个会话 ID，并将其存储为 HttpOnly 的 Cookie，然后重定向到授权页面。
     * 如果身份验证失败，会返回登录页面视图并显示错误信息。
     * </p>
     *
     * @param response    {@link HttpServletResponse}，用于向客户端添加 Cookie
     * @param clientId    客户端 ID
     * @param redirectUri 登录成功后的重定向 URI
     * @param username    用户名
     * @param password    密码
     * @param state       请求的状态参数，通常用于防止 CSRF 攻击
     * @return {@link ModelAndView}，重定向到授权页面或返回登录页面视图（如果登录失败）
     */
    @PostMapping(value = "/login")
    public ModelAndView login(HttpServletResponse response,
                             @RequestParam(name = "clientId") String clientId,
                             @RequestParam(name = "redirectUri") String redirectUri,
                             @RequestParam(name = "username") String username,
                             @RequestParam(name = "password") String password,
                             @RequestParam(name = "state", required = false) String state) {

        try {
            // 调用认证服务验证用户名和密码，获取会话 ID
            String sessionId = authService.authenticate(username, password);

            // 创建 HttpOnly Cookie
            Cookie cookie = new Cookie(Constants.SESSION_NAME, sessionId);
            cookie.setHttpOnly(true);             // 防止 JavaScript 访问 Cookie
            cookie.setSecure(false);               // 在 HTTPS 下才生效
            cookie.setPath("/");                  // 全站可用
            cookie.setMaxAge(24 * 60 * 60);       // 设置 Cookie 的有效期为 24 小时
            response.addCookie(cookie);           // 将 Cookie 添加到响应中

            // 登录成功，使用 ModelAndViewService 重定向到授权页面
            return modelAndViewService.redirectConsent(sessionId,clientId, redirectUri, state);
        } catch (Exception e) {
            // 登录失败，使用 ModelAndViewService 返回登录页面并显示错误信息，同时回显用户名
            log.error("登录错误, message:{}", e.getMessage(), e);
            return modelAndViewService.redirectLogin(clientId, redirectUri, state, "用户名或密码错误", username);
        }
    }

    /**
     * 用户登出接口，清除 Cookie 和 Redis 中的会话信息。
     * <p>
     * 该接口支持 GET 和 POST 请求，会从 Cookie 中读取会话 ID，删除 Redis 中的会话缓存，
     * 并清除客户端的 Cookie，然后重定向到首页。
     * </p>
     *
     * @param request  {@link HttpServletRequest}，用于获取 Cookie
     * @param response {@link HttpServletResponse}，用于清除 Cookie
     * @param sessionId 会话 ID（从 Cookie 中获取，可选）
     * @return {@link ModelAndView}，重定向到首页
     */
    @GetMapping(value = "/logout")
    @PostMapping(value = "/logout")
    public ModelAndView logout(HttpServletRequest request,
                               HttpServletResponse response,
                               @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId) {
        // 如果 Cookie 中没有 sessionId，尝试从请求中获取
        if (Strings.isBlank(sessionId)) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (Constants.SESSION_NAME.equals(cookie.getName())) {
                        sessionId = cookie.getValue();
                        break;
                    }
                }
            }
        }

        // 删除 Redis 中的会话缓存
        if (Strings.isNotBlank(sessionId)) {
            try {
                authService.logout(sessionId);
                log.debug("用户登出成功, sessionId: {}", sessionId);
            } catch (Exception e) {
                log.warn("删除会话缓存失败, sessionId: {}, error: {}", sessionId, e.getMessage());
            }
        }

        // 清除 Cookie（设置 MaxAge 为 0 并立即过期）
        Cookie cookie = new Cookie(Constants.SESSION_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 立即过期
        response.addCookie(cookie);

        // 返回前端跳转页：前端负责跳转到首页
        return new ModelAndView("logout");
    }
}
