package com.g2rain.iam.controller;


import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.SessionService;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;


/**
 * IAM 站内 HTML 入口：授权事务页（login / register）与非授权页（index / platform）分开映射。
 * <p>
 * 登录与注册须绑定有效 {@code tid} 与流程 Cookie；consent 不提供公开 GET，由编排直接渲染。
 * 无客户端上下文时不接受直接登录，统一回到业务侧再进 {@code /auth/authorize}。
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
@Controller
@AllArgsConstructor
public class PageController {

    /**
     * IAM / 平台对外地址，用于首页「立即登录」绝对跳转。
     */
    private IamAccessProperties iamAccessProperties;

    /**
     * 会话服务，用于首页登录态展示。
     */
    private SessionService sessionService;

    /**
     * 处理平台入口等重定向。
     */
    private ModelAndViewService modelAndViewService;

    /**
     * 授权事务 Redis 存储与状态迁移。
     */
    private AuthorizationTransactionService transactionService;

    /**
     * 授权流程 Cookie，用于绑定 {@code tid} 与当前浏览器。
     */
    private AuthFlowCookieService authFlowCookieService;

    /**
     * 基于 tid 的授权流程编排。
     */
    private AuthorizationFlowService authorizationFlowService;

    /**
     * 跳转至默认业务侧入口。
     * <p>
     * 页面不直接拼接业务平台地址，统一由服务端根据
     * {@code g2rain.iam.platform-base-url} 生成跳转目标。业务侧负责生成
     * Client DPoP 上下文（包括 {@code clientId}），再携带完整 OAuth 参数回到
     * {@code /auth/authorize}，IAM 不接受无客户端上下文的直接登录。
     * </p>
     */
    @GetMapping(value = "/auth/platform")
    public ModelAndView redirectToPlatform() {
        return modelAndViewService.redirectPlatformMainHome();
    }

    /**
     * IAM 首页：只认会话，不走授权事务。
     * <p>
     * 已登录展示账号摘要；{@code from=logout} 展示游客页；否则回业务控制台。
     * 授权必须从业务侧进入 {@code /auth/authorize}，本页不接收 OAuth 查询串。
     * </p>
     */
    @GetMapping(value = "/auth/index.html")
    public ModelAndView indexPage(
        @RequestParam(name = "redirectUri", required = false) String redirectUri,
        @RequestParam(name = "from", required = false) String from,
        @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
        HttpServletRequest request,
        Model model) {
        Optional<SessionDto> activeSession = resolveActiveSession(sessionId, request);
        if (activeSession.isPresent()) {
            applyLoggedInIndexModel(model, activeSession.get());
            return new ModelAndView("index", model.asMap());
        }
        if ("logout".equals(from)) {
            applyGuestIndexModel(model, redirectUri);
            return new ModelAndView("index", model.asMap());
        }
        return modelAndViewService.redirectPlatformMainHome();
    }

    /**
     * 登录页：须有效授权事务 {@code tid}（与注册页相同）。
     * <p>
     * 已登录则按事务继续编排；未登录渲染登录页。可选 {@code loginMethod} 只选择卡片，不是授权上下文。
     * 无 tid 不渲染登录表单。
     * </p>
     */
    @GetMapping(value = "/auth/login.html")
    public ModelAndView loginPage(
        HttpServletRequest request,
        @RequestParam(name = Constants.TID, required = false) String tid,
        @RequestParam(name = "loginMethod", required = false) String loginMethod,
        @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId) {
        if (Strings.isBlank(tid)) {
            return authorizationFlowService.renderFlowError(
                null, "请从业务侧重新发起授权后再登录");
        }
        try {
            AuthorizationTransactionDto txn = requireActiveTxn(request, tid);
            Optional<SessionDto> activeSession = resolveActiveSession(sessionId, request);
            if (activeSession.isPresent()) {
                return authorizationFlowService.continueFlow(txn, activeSession.get().getSessionId());
            }
            return authorizationFlowService.renderLogin(txn, null, null, loginMethod);
        } catch (Exception ex) {
            return authorizationFlowService.renderFlowError(null, ex.getMessage());
        }
    }

    /**
     * 注册页：须携带有效授权事务 {@code tid}。
     */
    @GetMapping(value = "/auth/register.html")
    public ModelAndView registerPage(
        HttpServletRequest request,
        @RequestParam(name = Constants.TID, required = false) String tid) {
        if (Strings.isBlank(tid)) {
            return authorizationFlowService.renderFlowError(
                null, "请从业务侧重新发起授权后再注册");
        }
        try {
            AuthorizationTransactionDto txn = requireActiveTxn(request, tid);
            return authorizationFlowService.renderRegister(txn);
        } catch (Exception ex) {
            return authorizationFlowService.renderFlowError(null, ex.getMessage());
        }
    }

    private AuthorizationTransactionDto requireActiveTxn(HttpServletRequest request, String tid) {
        String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
        return transactionService.requireActive(tid.trim(), flowHash);
    }

    private void applyGuestIndexModel(Model model, String redirectUri) {
        model.addAttribute("loggedIn", false);
        model.addAttribute("platformBaseUrl", resolvePlatformBaseUrl());
        String resolved = resolveGuestLoginRedirectUri(redirectUri);
        if (resolved == null) {
            model.addAttribute("loginViaRedirectUri", false);
        } else {
            model.addAttribute("loginViaRedirectUri", true);
            model.addAttribute("loginRedirectUri", resolved);
        }
    }

    /**
     * @return 合法跳转地址；若应使用默认控制台则返回 null
     */
    private static String resolveGuestLoginRedirectUri(String redirectUri) {
        if (Strings.isBlank(redirectUri)) {
            return null;
        }
        String trimmed = redirectUri.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return trimmed;
        }
        return null;
    }

    private void applyLoggedInIndexModel(Model model, SessionDto session) {
        model.addAttribute("loggedIn", true);
        model.addAttribute("platformBaseUrl", resolvePlatformBaseUrl());
        String accountName = resolveAccountDisplayName(session);
        model.addAttribute("accountName", accountName);
        model.addAttribute("passportId", resolvePassportDisplay(session.getPassportId()));
        model.addAttribute("loginMethod", resolveLoginMethod(session.getIdpType()));
        String bindModeLabel = resolveIdpBindModeLabel(session.getIdpBindMode());
        if (Strings.isNotBlank(bindModeLabel)) {
            model.addAttribute("idpBindModeLabel", bindModeLabel);
        }
    }

    private static String resolveAccountDisplayName(SessionDto session) {
        if (Strings.isNotBlank(session.getName())) {
            return session.getName().trim();
        }
        if (Strings.isNotBlank(session.getPassportId())) {
            return "通行证 " + session.getPassportId().trim();
        }
        if (Strings.isNotBlank(session.getIdpSubject())) {
            return session.getIdpSubject().trim();
        }
        return "当前用户";
    }

    private static String resolvePassportDisplay(String passportId) {
        return Strings.isNotBlank(passportId) ? passportId.trim() : "—";
    }

    Optional<SessionDto> resolveActiveSession(String cookieSessionId, HttpServletRequest request) {
        String resolvedSessionId = cookieSessionId;
        if (Strings.isBlank(resolvedSessionId) && request != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (Constants.SESSION_NAME.equals(cookie.getName())) {
                        resolvedSessionId = cookie.getValue();
                        break;
                    }
                }
            }
        }
        if (Strings.isBlank(resolvedSessionId)) {
            return Optional.empty();
        }
        SessionDto session = sessionService.getSession(resolvedSessionId.trim());
        return session == null ? Optional.empty() : Optional.of(session);
    }

    private static String resolveLoginMethod(String idpType) {
        if (Strings.isBlank(idpType)) {
            return "账号密码";
        }
        return switch (idpType.trim()) {
            case "DINGTALK" -> "钉钉";
            case "WECHAT_WORK" -> "企业微信";
            default -> idpType.trim();
        };
    }

    private static String resolveIdpBindModeLabel(String bindMode) {
        if (Strings.isBlank(bindMode)) {
            return "";
        }
        return switch (bindMode.trim()) {
            case "INTERNAL" -> "企业内部应用";
            case "THIRD_PARTY" -> "第三方企业应用";
            default -> bindMode.trim();
        };
    }

    /**
     * 控制台对外根 URL（无尾斜杠）：{@code platform-base-url}，未配置时回退为 IAM {@code base-url}。
     */
    private String resolvePlatformBaseUrl() {
        return iamAccessProperties.resolvedPlatformBaseUrl();
    }
}
