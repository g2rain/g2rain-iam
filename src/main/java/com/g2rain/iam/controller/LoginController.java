package com.g2rain.iam.controller;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthPolicyGuard;
import com.g2rain.iam.service.AuthService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.IamSessionCookieService;
import com.g2rain.iam.service.SessionService;
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
 * 登录控制器，处理账号密码登录与本浏览器退出。
 * <p>
 * 登录接口只接收授权事务 {@code tid}，不再透传完整 OAuth 参数；验证通过后写入会话 Cookie，
 * 并由 {@link AuthorizationFlowService} 按事务状态继续编排。退出时取消未完成授权事务并清除会话与 flow Cookie。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 POST 请求进行用户登录（仅 tid）
 * POST /auth/login
 * Content-Type: application/x-www-form-urlencoded
 *
 * tid=...&username=user123&password=secret
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
     * 会话服务，用于删除 Redis 中的会话缓存。
     */
    private final SessionService sessionService;

    /**
     * IAM 会话 Cookie 写入与清理。
     */
    private final IamSessionCookieService iamSessionCookieService;

    /**
     * 授权事务 Redis 存储与状态迁移。
     */
    private final AuthorizationTransactionService transactionService;

    /**
     * 授权流程 Cookie，用于绑定 {@code tid} 与当前浏览器。
     */
    private final AuthFlowCookieService authFlowCookieService;

    /**
     * 基于 tid 的授权流程编排。
     */
    private final AuthorizationFlowService authorizationFlowService;

    /**
     * 登录策略闸门。
     */
    private final AuthPolicyGuard authPolicyGuard;

    /**
     * 用户登录接口：在授权事务内校验账号密码并继续流程。
     * <p>
     * 成功则写入 HttpOnly 会话 Cookie，并调用 {@link AuthorizationFlowService#afterPasswordLogin}；
     * 失败则回到同一事务的登录页并回显错误。
     * </p>
     *
     * @param request  当前 HTTP 请求（校验 flow Cookie）
     * @param response 当前 HTTP 响应（写入会话 Cookie）
     * @param tid      授权事务 ID
     * @param username 用户名
     * @param password 密码
     * @return 流程续跑视图、登录页（失败）或错误页
     */
    @PostMapping(value = "/login")
    public ModelAndView login(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name = Constants.TID) String tid,
        @RequestParam(name = "username") String username,
        @RequestParam(name = "password") String password) {

        if (Strings.isBlank(tid)) {
            return authorizationFlowService.renderFlowError(
                null, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
        }

        try {
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireActive(tid.trim(), flowHash);
            authPolicyGuard.requirePassword(txn);
            String sessionId = authService.authenticate(username, password);
            iamSessionCookieService.writeSessionCookie(response, sessionId);
            return authorizationFlowService.afterPasswordLogin(txn, sessionId);
        } catch (BusinessException ex) {
            try {
                String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
                AuthorizationTransactionDto txn = transactionService.requireReadable(tid.trim(), flowHash);
                return authorizationFlowService.renderLogin(txn, ex.getMessage(), username);
            } catch (BusinessException ignored) {
                return authorizationFlowService.renderFlowError(null, ex.getMessage());
            }
        } catch (Exception e) {
            log.error("登录错误, message:{}", e.getMessage(), e);
            try {
                String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
                AuthorizationTransactionDto txn = transactionService.requireReadable(tid.trim(), flowHash);
                return authorizationFlowService.renderLogin(txn, "用户名或密码错误", username);
            } catch (BusinessException ex) {
                return authorizationFlowService.renderFlowError(null, ex.getMessage());
            }
        }
    }

    /**
     * 退出确认页（GET）：要求用户二次确认后再真正退出。
     *
     * @return 退出确认视图
     */
    @GetMapping(value = "/logout")
    public ModelAndView logoutGet() {
        ModelAndView mv = new ModelAndView("logout");
        mv.getModelMap().addAttribute("confirmLogout", true);
        mv.getModelMap().addAttribute("error", "请确认退出当前登录");
        return mv;
    }

    /**
     * 用户登出接口：取消未完成授权事务，并清理会话 Cookie 与 Redis 会话。
     * <p>
     * 仅影响本浏览器会话；已签发 JWT 与刷新能力仍按原生命周期有效。
     * 会校验 {@code Origin}/{@code Referer} 与 Host 同源，拒绝跨站退出。
     * </p>
     *
     * @param request   {@link HttpServletRequest}，用于获取 Cookie 与来源头
     * @param response  {@link HttpServletResponse}，用于清理 Cookie
     * @param sessionId 会话 ID（从 Cookie 中获取，可选）
     * @return 退出结果视图
     */
    @PostMapping(value = "/logout")
    public ModelAndView logout(
        HttpServletRequest request,
        HttpServletResponse response,
        @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId) {

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        if (!isSameOrigin(request, origin, referer)) {
            ModelAndView mv = new ModelAndView("logout");
            mv.getModelMap().addAttribute("error", "非法来源，退出被拒绝");
            return mv;
        }

        String flowRaw = authFlowCookieService.readRaw(request);
        String flowHash = authFlowCookieService.hash(flowRaw);
        boolean failed = false;

        try {
            if (Strings.isNotBlank(flowHash)) {
                transactionService.cancelAllByFlowHash(flowHash);
            }
        } catch (Exception e) {
            failed = true;
            log.warn("取消授权事务失败: {}", e.getMessage());
        }

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

        if (Strings.isNotBlank(sessionId)) {
            try {
                sessionService.logout(sessionId);
            } catch (Exception e) {
                failed = true;
                log.warn("删除会话缓存失败, sessionId: {}, error: {}", sessionId, e.getMessage());
            }
        }

        iamSessionCookieService.clearSessionCookie(response);
        authFlowCookieService.clear(response);

        ModelAndView mv = new ModelAndView("logout");
        if (failed) {
            mv.getModelMap().addAttribute("error", "退出未完全成功，请重试");
        }
        return mv;
    }

    /**
     * 校验请求是否与当前 Host 同源（基于 Origin / Referer）。
     *
     * @param request 当前请求
     * @param origin  Origin 头
     * @param referer Referer 头
     * @return 同源或无法判定时返回 {@code true}
     */
    private static boolean isSameOrigin(HttpServletRequest request, String origin, String referer) {
        String host = request.getHeader("Host");
        if (Strings.isBlank(host)) {
            return true;
        }
        if (Strings.isNotBlank(origin)) {
            return origin.contains(host);
        }
        if (Strings.isNotBlank(referer)) {
            return referer.contains(host);
        }
        return true;
    }
}
