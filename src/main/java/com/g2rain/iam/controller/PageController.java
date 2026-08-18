package com.g2rain.iam.controller;


import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.SessionService;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;


/**
 * 页面控制器，负责渲染登录和授权同意页面。
 * <p>
 * 该控制器用于处理客户端的登录和授权请求，渲染登录页面和授权同意页面，并在需要时处理用户会话。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 GET 请求跳转到登录页
 * /auth/login?clientId=client123&redirectUri=http://example.com/callback
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
@Controller
@AllArgsConstructor
public class PageController {

    /**
     * 资源加载器，用于检查模板文件是否存在。
     */
    private ResourceLoader resourceLoader;

    /**
     * IAM / 平台对外地址，用于首页「立即登录」绝对跳转。
     */
    private IamAccessProperties iamAccessProperties;

    /**
     * 登录页钉钉入口使用的 {@code bindMode} 等配置。
     */
    private DingTalkIamProperties dingTalkIamProperties;

    /**
     * 登录页企业微信入口配置。
     */
    private WeComIamProperties weComIamProperties;

    private SessionService sessionService;

    private ModelAndViewService modelAndViewService;

    /**
     * 注册页面渲染方法，处理 /auth/register.html 路径。
     * <p>
     * 该方法专门用于渲染注册页面，接收 OAuth 授权流程中的参数（clientId、redirectUri、state），
     * 并将这些参数传递给模板，以便在注册完成后能够正确跳转回登录页面。
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 通过 GET 请求跳转到注册页
     * /auth/register.html?clientId=client123&redirectUri=http://example.com/callback&state=xyz
     * }</pre>
     * </p>
     *
     * @param clientId    客户端 ID（可选）
     * @param redirectUri 登录后重定向的 URI（可选）
     * @param state       请求的状态参数，通常用于防止 CSRF 攻击（可选）
     * @param model       用于向视图传递数据的模型
     * @return {@link ModelAndView}，包含注册页面视图
     */
    @GetMapping(value = "/auth/register.html")
    public ModelAndView registerPage(@RequestParam(name = "clientId", required = false) String clientId,
                                    @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                    @RequestParam(name = "state", required = false) String state,
                                    Model model) {
        // 将 URL 参数传递到视图，供注册表单使用
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state);
        return new ModelAndView("register");
    }

    /**
     * 通用页面渲染方法，处理 /auth/*.html 路径。
     * <p>
     * 该方法会根据路径中的文件名查找对应的模板文件。如果模板存在，则渲染该模板；
     * 如果模板不存在，则返回错误页面并提示路径不存在。
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 访问 /auth/index.html 会渲染 templates/index.html
     * // 访问 /auth/test.html 如果模板不存在，会显示错误页面
     * }</pre>
     * </p>
     *
     * @param filename 模板文件名（不包含 .html 后缀）
     * @param model    用于向视图传递数据的模型
     * @return {@link ModelAndView}，包含模板视图或错误页面视图
     */
    @GetMapping(value = "/auth/{filename}.html")
    public ModelAndView dynamicPage(@PathVariable(name = "filename") String filename,
                                    @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                    @RequestParam(name = "clientId", required = false) String clientId,
                                    @RequestParam(name = "state", required = false) String state,
                                    @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
                                    HttpServletRequest request,
                                    Model model) {
        // 防止路径遍历攻击，确保文件名只包含合法字符
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            model.addAttribute("error", "非法的路径参数");
            model.addAttribute("redirectUri", "");
            return new ModelAndView("error");
        }

        // 检查模板文件是否存在
        String templatePath = "classpath:/templates/" + filename + ".html";
        Resource resource = resourceLoader.getResource(templatePath);

        if (resource.exists() && resource.isReadable()) {
            Optional<SessionDto> activeSession = resolveActiveSession(sessionId, request);

            if ("index".equals(filename)) {
                if (activeSession.isEmpty()) {
                    return new ModelAndView(Constants.REDIRECT + buildLoginPageUrl(clientId, redirectUri, state));
                }
                applyLoggedInIndexModel(model, activeSession.get());
                return new ModelAndView(filename, model.asMap());
            }

            if ("login".equals(filename)) {
                if (activeSession.isPresent()) {
                    SessionDto session = activeSession.get();
                    if (Strings.isNotBlank(clientId) && Strings.isNotBlank(redirectUri)) {
                        return modelAndViewService.redirectConsent(
                            session.getSessionId(), clientId, redirectUri, state);
                    }
                    return new ModelAndView(Constants.REDIRECT + "/auth/index.html");
                }
                applyLoginPageModel(model, clientId, redirectUri, state);
                return new ModelAndView(filename, model.asMap());
            }

            return new ModelAndView(filename);
        }

        // 模板不存在，返回错误页面
        String requestPath = "/auth/" + filename + ".html";
        model.addAttribute("error", "请求的页面不存在: " + requestPath);
        model.addAttribute("redirectUri", "");
        return new ModelAndView("error");
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

    private void applyLoginPageModel(Model model, String clientId, String redirectUri, String state) {
        model.addAttribute("clientId", clientId != null ? clientId : "");
        model.addAttribute("redirectUri", redirectUri != null ? redirectUri : "");
        model.addAttribute("state", state != null ? state : "");
        String m = dingTalkIamProperties.getLoginPageBindMode();
        if (Strings.isNotBlank(m)) {
            model.addAttribute("dingTalkBindMode", m.trim());
        }
        String weComMode = weComIamProperties.getLoginPageBindMode();
        if (Strings.isNotBlank(weComMode)) {
            model.addAttribute("weComBindMode", weComMode.trim());
        }
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

    String buildLoginPageUrl(String clientId, String redirectUri, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/auth/login.html");
        if (Strings.isNotBlank(clientId)) {
            builder.queryParam("clientId", clientId.trim());
        }
        if (Strings.isNotBlank(redirectUri)) {
            builder.queryParam("redirectUri", redirectUri.trim());
        }
        if (Strings.isNotBlank(state)) {
            builder.queryParam("state", state);
        }
        return builder.build(true).toUriString();
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
