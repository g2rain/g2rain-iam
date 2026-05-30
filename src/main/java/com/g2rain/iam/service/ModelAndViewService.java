package com.g2rain.iam.service;

import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.utils.Constants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ModelAndView 服务类，用于处理视图重定向和错误页面的逻辑。
 */
@Service
public class ModelAndViewService {

    /**
     * 授权服务，处理授权码的生成等业务逻辑。
     */
    @Resource
    private AuthorizationService authorizationService;
    /**
     * 用户服务，提供与用户相关的业务逻辑。
     */
    @Resource
    private UserService userService;

    @Resource
    private SessionService sessionService;

    @Resource
    private IamAccessProperties iamAccessProperties;

    @Resource
    private DingTalkIamProperties dingTalkIamProperties;

    /**
     * 当客户端 ID 或重定向 URI 为空时，返回错误页面。
     * <p>
     * 该方法会检查客户端 ID 和重定向 URI 是否为空。如果为空，则构造一个错误页面并设置相应的错误信息。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @return {@link ModelAndView}，包含错误信息和重定向 URI 的视图模型
     */
    public ModelAndView redirectError(String clientId, String redirectUri, String state) {
        // 验证 clientId 和 redirectUri 是否为空
        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            // 如果为空，返回 error.html 页面
            ModelAndView modelAndView = new ModelAndView("error");
            ModelMap model = modelAndView.getModelMap();

            // 设置错误信息
            String errorMessage = null;
            if (Strings.isBlank(clientId)) {
                errorMessage = SystemErrorCode.PARAM_REQUIRED.getMessage("clientId");
            }
            if (Strings.isBlank(redirectUri)) {
                if (Strings.isBlank(clientId)) {
                    errorMessage += "，";
                }
                errorMessage += SystemErrorCode.PARAM_REQUIRED.getMessage("redirectUri");
            }

            model.addAttribute("error", errorMessage);
            // 如果 redirectUri 为空，设置为空字符串，前端会跳转到 index.html
            model.addAttribute("redirectUri", Strings.isBlank(redirectUri) ? "" : redirectUri);
            model.addAttribute("state", state != null ? state : "");

            return modelAndView;
        }

        return null;
    }


    /**
     * 构造并返回登录页面的视图。
     * <p>
     * 该方法会根据客户端 ID、重定向 URI 和状态值生成登录页面视图。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @param error       错误信息（可选），如果提供则会在登录页面显示
     * @param username    用户名（可选），如果提供则会在登录页面回显
     * @return {@link ModelAndView}，包含登录页面视图
     */
    public ModelAndView redirectLogin(String clientId, String redirectUri, String state, String error, String username) {
        ModelAndView modelAndView = new ModelAndView("login");
        ModelMap model = modelAndView.getModelMap();
        // 将参数传递到视图
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state);
        if (Strings.isNotBlank(error)) {
            model.addAttribute("error", error);
        }
        if (Strings.isNotBlank(username)) {
            model.addAttribute("username", username);
        }
        String bindMode = loginPageDingTalkBindModeOrNull();
        if (bindMode != null) {
            model.addAttribute("dingTalkBindMode", bindMode);
        }
        return modelAndView;
    }

    /**
     * @return 非空则启用登录页钉钉入口；未配置时返回 {@code null}
     */
    private String loginPageDingTalkBindModeOrNull() {
        String m = dingTalkIamProperties.getLoginPageBindMode();
        return Strings.isBlank(m) ? null : m.trim();
    }

    /**
     * 构造并返回登录页面的视图（带错误信息）。
     * <p>
     * 该方法会根据客户端 ID、重定向 URI 和状态值生成登录页面视图。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @param error       错误信息（可选），如果提供则会在登录页面显示
     * @return {@link ModelAndView}，包含登录页面视图
     */
    public ModelAndView redirectLogin(String clientId, String redirectUri, String state, String error) {
        return redirectLogin(clientId, redirectUri, state, error, null);
    }

    /**
     * 构造并返回登录页面的视图（无错误信息版本）。
     * <p>
     * 该方法会根据客户端 ID、重定向 URI 和状态值生成登录页面视图。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @return {@link ModelAndView}，包含登录页面视图
     */
    public ModelAndView redirectLogin(String clientId, String redirectUri, String state) {
        return redirectLogin(clientId, redirectUri, state, null, null);
    }

    /**
     * 重定向到业务平台控制台首页（无 OAuth {@code clientId} 时，注册完成后的默认去向）。
     *
     * @return {@code redirect:}{@link IamAccessProperties#resolvedPlatformBaseUrl()}{@code /main/home}
     */
    public ModelAndView redirectPlatformMainHome() {
        String base = iamAccessProperties.resolvedPlatformBaseUrl();
        return new ModelAndView(Constants.REDIRECT + base + "/main/home");
    }

    /**
     * 重定向到授权页面。
     * <p>
     * 该方法会构造授权页面的重定向 URL，包含客户端 ID、重定向 URI 和状态参数。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 授权后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @return {@link ModelAndView}，包含重定向到授权页面的视图
     */
    public ModelAndView redirectAuthorize(String clientId, String redirectUri, String state) {
        UriComponentsBuilder authorizeUrl = UriComponentsBuilder.fromPath("/auth/authorize")
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam(Constants.REDIRECT_URI, redirectUri);

        if (Strings.isNotBlank(state)) {
            authorizeUrl.queryParam(Constants.STATE, state);
        }

        return new ModelAndView(Constants.REDIRECT + authorizeUrl.build().toUriString());
    }

    /**
     * 重定向到授权同意页面（consent 页面）。
     * <p>
     * 该方法首先验证用户会话的有效性。如果会话 ID 为空或会话已过期，则重定向到登录页面；
     * 如果会话有效，则获取用户列表并返回授权同意页面，供用户选择授权。
     * </p>
     * <p>
     * 使用流程：
     * <ol>
     *     <li>检查 sessionId 是否为空，如果为空则重定向到登录页面</li>
     *     <li>验证会话是否有效，如果会话无效或已过期则重定向到登录页面</li>
     *     <li>获取当前会话关联的用户列表</li>
     *     <li>返回授权同意页面视图，包含用户列表和授权参数</li>
     * </ol>
     * </p>
     *
     * @param sessionId   当前用户会话 ID，用于验证用户登录状态
     * @param clientId    客户端 ID，标识发起授权请求的客户端应用
     * @param redirectUri 授权后重定向的 URI，用于 OAuth2 回调
     * @param state       请求的状态参数，通常用于防止 CSRF 攻击
     * @return {@link ModelAndView}，包含授权同意页面的视图。如果会话无效，则返回登录页面的重定向视图
     */
    public ModelAndView redirectConsent(String sessionId, String clientId, String redirectUri, String state) {
        // 如果 sessionId 为空，则跳转到登录页面
        if (Strings.isBlank(sessionId)) {
            return this.redirectLogin(clientId, redirectUri, state);
        }

        // 获取当前会话，若会话为空，则跳转到登录页面
        SessionDto session = sessionService.getSession(sessionId);
        if (Objects.isNull(session)) {
            return this.redirectLogin(clientId, redirectUri, state);
        }

        // 获取用户列表
        List<Map<String, String>> maps = userService.listUsers(session);
        if (CollectionUtils.isEmpty(maps) || maps.size() == 1) {
            String userId = null;
            if (!CollectionUtils.isEmpty(maps)) {
                userId = maps.getFirst().get("id");
            }

            return redirectCallback(sessionId, userId, clientId, redirectUri, state);
        }

        ModelAndView modelAndView = new ModelAndView("consent");
        ModelMap model = modelAndView.getModelMap();
        // 将数据添加到模型中
        model.addAttribute("users", maps);
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state);
        return modelAndView;
    }

    public ModelAndView redirectCallback(String sessionId, String userId, String clientId, String redirectUri, String state) {
        // 如果 sessionId 为空，则跳转到登录页面
        if (Strings.isBlank(sessionId)) {
            return this.redirectLogin(clientId, redirectUri, state);
        }

        // 获取当前会话，若会话为空，则跳转到登录页面
        SessionDto session = sessionService.getSession(sessionId);
        if (Objects.isNull(session)) {
            return this.redirectLogin(clientId, redirectUri, state);
        }
        // 生成授权码（会话带 IdP 信息时视为外部身份源授权链路）
        boolean thirdPartyIdpLogin = Strings.isNotBlank(session.getIdpType());
        String code = authorizationService.generateAuthorizationCode(session, clientId, userId, thirdPartyIdpLogin);

        // 构造重定向 URL
        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam(Constants.CODE, code);

        if (Strings.isNotBlank(state)) {
            redirectUrl.queryParam(Constants.STATE, state);
        }

        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }
}
