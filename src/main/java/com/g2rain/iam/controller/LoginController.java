package com.g2rain.iam.controller;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
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
 * 登录 / 退出：登录只收 tid；退出取消未完成授权事务。
 */
@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth")
public class LoginController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final IamSessionCookieService iamSessionCookieService;
    private final AuthorizationTransactionService transactionService;
    private final AuthFlowCookieService authFlowCookieService;
    private final AuthorizationFlowService authorizationFlowService;

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

    @GetMapping(value = "/logout")
    public ModelAndView logoutGet() {
        ModelAndView mv = new ModelAndView("logout");
        mv.getModelMap().addAttribute("confirmLogout", true);
        mv.getModelMap().addAttribute("error", "请确认退出当前登录");
        return mv;
    }

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
