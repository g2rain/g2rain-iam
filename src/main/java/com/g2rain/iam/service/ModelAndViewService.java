package com.g2rain.iam.service;

import com.g2rain.basis.dto.ApplicationAuthorizationActivateSelfRequest;
import com.g2rain.basis.dto.ApplicationSelectDto;
import com.g2rain.basis.dto.OrganIdNameMapSelectDto;
import com.g2rain.basis.vo.ApplicationAuthorizationActivateSelfVo;
import com.g2rain.basis.vo.ApplicationVo;
import com.g2rain.basis.vo.OrganIdNameVo;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.ApplicationAuthorizationClient;
import com.g2rain.iam.client.ApplicationClient;
import com.g2rain.iam.client.OrganClient;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.ConsentPreviewDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.utils.AuthorizationState;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUrlUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ModelAndView 服务类，用于处理视图重定向、错误页面及 OAuth 授权确认编排。
 * <p>
 * 无 {@code applicationCode}：沿用原逻辑——单用户直接发码回调；多用户先选用户，选定后发码。
 * 有 {@code applicationCode}：进入应用授权 consent，确认后调用 Basis 开通再发码。
 * </p>
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

    @Resource
    private WeComIamProperties weComIamProperties;

    /**
     * Basis 应用授权（SELF 开通）；consent 预览由 IAM 本地拼装。
     */
    @Resource
    private ApplicationAuthorizationClient applicationAuthorizationClient;

    @Resource
    private ApplicationClient applicationClient;

    @Resource
    private OrganClient organClient;

    /**
     * 当客户端 ID 或重定向 URI 为空时，返回错误页面。
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向 URI
     * @param state       防止 CSRF 的状态参数
     * @return 错误视图；参数齐全时返回 {@code null}
     */
    public ModelAndView redirectError(String clientId, String redirectUri, String state) {
        // 验证 clientId 与 redirectUri 是否为空
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
     * OAuth 流程错误页（clientId / redirectUri 已校验非空）。
     */
    public ModelAndView redirectOAuthError(String clientId, String redirectUri, String state, String errorMessage) {
        ModelAndView modelAndView = new ModelAndView("error");
        ModelMap model = modelAndView.getModelMap();
        model.addAttribute("error", errorMessage);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state != null ? state : "");
        return modelAndView;
    }

    public ModelAndView redirectLogin(String clientId, String redirectUri, String state, String error, String username) {
        return redirectLogin(clientId, redirectUri, state, null, error, username);
    }

    /**
     * 构造并返回登录页面视图（可携带 {@code applicationCode} 与错误、用户名回显）。
     */
    public ModelAndView redirectLogin(String clientId, String redirectUri, String state,
                                      String applicationCode, String error, String username) {
        ModelAndView modelAndView = new ModelAndView("login");
        ModelMap model = modelAndView.getModelMap();
        // 将参数传递到视图
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state);
        model.addAttribute("applicationCode", Strings.isBlank(applicationCode) ? "" : applicationCode.trim());
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
        String weComBindMode = loginPageWeComBindModeOrNull();
        if (weComBindMode != null) {
            model.addAttribute("weComBindMode", weComBindMode);
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

    private String loginPageWeComBindModeOrNull() {
        String mode = weComIamProperties.getLoginPageBindMode();
        return Strings.isBlank(mode) ? null : mode.trim();
    }

    /**
     * 构造并返回登录页面视图（无错误信息）。
     */
    public ModelAndView redirectLogin(String clientId, String redirectUri, String state) {
        return redirectLogin(clientId, redirectUri, state, null, null, null);
    }

    public ModelAndView redirectLogin(String clientId, String redirectUri, String state, String applicationCode) {
        return redirectLogin(clientId, redirectUri, state, applicationCode, null, null);
    }

    /**
     * 重定向到业务平台控制台首页（无 OAuth {@code clientId} 时，注册完成后的默认去向）。
     *
     * @return {@code redirect:}{@link IamAccessProperties#resolvedPlatformBaseUrl()}{@code /main/home}
     */
    public ModelAndView redirectPlatformMainHome() {
        String url = IamUrlUtils.joinAbsoluteUrl(
            iamAccessProperties.resolvedPlatformBaseUrl(), "/main", "/home");
        return new ModelAndView(Constants.REDIRECT + url);
    }

    public ModelAndView redirectAuthorize(String clientId, String redirectUri, String state) {
        return redirectAuthorize(clientId, redirectUri, state, null);
    }

    /**
     * 重定向到 {@code /auth/authorize}，携带 OAuth 参数及可选 {@code applicationCode}。
     */
    public ModelAndView redirectAuthorize(String clientId, String redirectUri, String state, String applicationCode) {
        UriComponentsBuilder authorizeUrl = UriComponentsBuilder.fromPath("/auth/authorize")
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam(Constants.REDIRECT_URI, redirectUri);

        if (Strings.isNotBlank(state)) {
            authorizeUrl.queryParam(Constants.STATE, state);
        }
        if (Strings.isNotBlank(applicationCode)) {
            authorizeUrl.queryParam(Constants.APPLICATION_CODE, applicationCode.trim());
        }

        return new ModelAndView(Constants.REDIRECT + authorizeUrl.build().toUriString());
    }

    public ModelAndView redirectConsent(String sessionId, String clientId, String redirectUri, String state) {
        return redirectConsent(sessionId, clientId, redirectUri, state, null);
    }

    public ModelAndView redirectConsent(String sessionId, String clientId, String redirectUri,
                                        String state, String applicationCode) {
        return redirectConsent(sessionId, clientId, redirectUri, state, applicationCode, null);
    }

    /**
     * 登录后授权编排入口。
     * <p>
     * 无 {@code applicationCode}：单用户或已选用户直接 {@link #redirectCallback} 发码；
     * 多用户未选则渲染选用户页。有 {@code applicationCode}：绑定会话、展示 consent 预览，
     * 须用户确认后才发码。
     * </p>
     *
     * @param sessionId       当前用户会话 ID
     * @param clientId        客户端 ID
     * @param redirectUri     授权后重定向 URI
     * @param state           状态参数
     * @param applicationCode 目标应用编码（可选）
     * @param selectedUserId  预选用户 ID（可选）
     */
    public ModelAndView redirectConsent(String sessionId, String clientId, String redirectUri,
                                        String state, String applicationCode, String selectedUserId) {
        // 如果 sessionId 为空，则跳转到登录页面
        if (Strings.isBlank(sessionId)) {
            return this.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        // 获取当前会话，若会话为空，则跳转到登录页面
        SessionDto session = sessionService.getSession(sessionId);
        if (Objects.isNull(session)) {
            return this.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        List<UserVo> users = userService.listUserVos(session);

        // 无 applicationCode：原逻辑——有确定用户则直接发码，否则仅选用户
        if (Strings.isBlank(applicationCode)) {
            String legacyUserId = resolveSelectedUserId(users, selectedUserId);
            if (Strings.isNotBlank(legacyUserId)) {
                return redirectCallback(sessionId, legacyUserId, clientId, redirectUri, state, null);
            }
            if (Strings.isNotBlank(selectedUserId)) {
                return redirectOAuthError(
                    clientId,
                    redirectUri,
                    AuthorizationState.resolveCallbackState(state),
                    "所选用户不属于当前登录账号"
                );
            }
            return buildConsentView(clientId, redirectUri, state, "", users);
        }

        sessionService.bindOAuthConsent(sessionId, clientId, redirectUri, applicationCode.trim(), state);

        ModelAndView modelAndView = buildConsentView(clientId, redirectUri, state, applicationCode.trim(), users);
        ModelMap model = modelAndView.getModelMap();

        String resolvedUserId = resolveSelectedUserId(users, selectedUserId);
        if (Strings.isNotBlank(resolvedUserId)) {
            model.addAttribute("selectedUserId", resolvedUserId);
            UserVo selected = users.stream()
                .filter(u -> Objects.equals(String.valueOf(u.getId()), resolvedUserId))
                .findFirst()
                .orElse(null);
            populatePreview(model, selected, applicationCode.trim());
        } else if (Strings.isNotBlank(selectedUserId)) {
            model.addAttribute("previewError", "所选用户不属于当前登录账号");
        }
        return modelAndView;
    }

    private static String resolveSelectedUserId(List<UserVo> users, String selectedUserId) {
        if (Strings.isNotBlank(selectedUserId)) {
            String trimmed = selectedUserId.trim();
            boolean allowed = users.stream()
                .anyMatch(u -> Objects.equals(String.valueOf(u.getId()), trimmed));
            return allowed ? trimmed : null;
        }
        if (users.size() == 1) {
            return String.valueOf(users.getFirst().getId());
        }
        return null;
    }

    private static List<Map<String, String>> toUserMaps(List<UserVo> users) {
        return users.stream()
            .map(u -> Map.of(
                "id", String.valueOf(u.getId()),
                "username", u.getRealName() == null ? "" : u.getRealName()
            ))
            .toList();
    }

    private ModelAndView buildConsentView(
        String clientId, String redirectUri, String state, String applicationCode, List<UserVo> users) {
        ModelAndView modelAndView = new ModelAndView("consent");
        ModelMap model = modelAndView.getModelMap();
        model.addAttribute("users", toUserMaps(users));
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state != null ? state : "");
        model.addAttribute("applicationCode", applicationCode == null ? "" : applicationCode);
        return modelAndView;
    }

    /**
     * 用户确认或拒绝授权：拒绝则客户端 {@code access_denied}；确认则发码或先 {@code activate_self} 再发码。
     */
    public ModelAndView confirmConsent(
        String sessionId,
        String userId,
        String clientId,
        String redirectUri,
        String state,
        String applicationCode,
        boolean denied) {
        if (Strings.isBlank(sessionId)) {
            return redirectLogin(clientId, redirectUri, state, applicationCode);
        }
        SessionDto session = sessionService.getSession(sessionId);
        if (session == null) {
            return redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        String callbackState = AuthorizationState.resolveCallbackState(state);
        if (denied) {
            if (Strings.isNotBlank(applicationCode)) {
                sessionService.clearOAuthConsent(sessionId);
            }
            return redirectClientError(clientId, redirectUri, callbackState, "access_denied");
        }

        if (Strings.isBlank(userId)) {
            return redirectOAuthError(
                clientId, redirectUri, callbackState,
                SystemErrorCode.PARAM_REQUIRED.getMessage("userId"));
        }
        String selectedUserId = userId.trim();
        boolean allowed = userService.listUserVos(session).stream()
            .anyMatch(u -> Objects.equals(String.valueOf(u.getId()), selectedUserId));
        if (!allowed) {
            return redirectOAuthError(
                clientId, redirectUri, callbackState, "所选用户不属于当前登录账号");
        }

        if (Strings.isBlank(applicationCode)) {
            return redirectCallback(sessionId, selectedUserId, clientId, redirectUri, state, null);
        }

        try {
            sessionService.requireOAuthConsentMatching(sessionId, clientId, redirectUri, applicationCode);
        } catch (BusinessException ex) {
            return redirectOAuthError(clientId, redirectUri, callbackState, ex.getMessage());
        }

        Long parsedUserId;
        try {
            parsedUserId = Long.valueOf(selectedUserId);
        } catch (NumberFormatException ex) {
            return redirectOAuthError(
                clientId, redirectUri, callbackState,
                SystemErrorCode.PARAM_VAL_INVALID.getMessage("userId"));
        }

        ApplicationAuthorizationActivateSelfRequest request = new ApplicationAuthorizationActivateSelfRequest();
        request.setApplicationCode(applicationCode.trim());
        request.setUserId(parsedUserId);

        Result<ApplicationAuthorizationActivateSelfVo> result;
        try {
            result = applicationAuthorizationClient.activateSelf(request);
        } catch (Exception ex) {
            return redirectOAuthError(clientId, redirectUri, callbackState, "开通应用失败，请稍后重试");
        }
        if (!result.isSuccess() || result.getData() == null) {
            String message = result.getErrorMessage();
            return redirectOAuthError(
                clientId,
                redirectUri,
                callbackState,
                Strings.isBlank(message) ? "开通应用失败" : message
            );
        }

        ApplicationAuthorizationActivateSelfVo activated = result.getData();
        boolean thirdPartyIdpLogin = Strings.isNotBlank(session.getIdpType());
        String code = authorizationService.generateAuthorizationCode(
            session,
            clientId,
            selectedUserId,
            thirdPartyIdpLogin,
            applicationCode.trim(),
            activated.getApplicationId(),
            activated.getOrganId()
        );
        sessionService.clearOAuthConsent(sessionId);

        // 构造重定向 URL
        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam(Constants.CODE, code);
        if (Strings.isNotBlank(callbackState)) {
            redirectUrl.queryParam(Constants.STATE, callbackState);
        }
        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }

    public ModelAndView redirectCallback(String sessionId, String userId, String clientId,
                                         String redirectUri, String state) {
        return redirectCallback(sessionId, userId, clientId, redirectUri, state, null);
    }

    /**
     * 常规 OAuth 确认后发码并重定向到客户端（不含 {@code applicationCode} 绑定流程）。
     */
    public ModelAndView redirectCallback(String sessionId, String userId, String clientId,
                                         String redirectUri, String state, String applicationCode) {
        if (Strings.isNotBlank(applicationCode)) {
            throw new IllegalStateException("带 applicationCode 的确认必须经应用授权确认后发码");
        }

        // 如果 sessionId 为空，则跳转到登录页面
        if (Strings.isBlank(sessionId)) {
            return this.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        // 获取当前会话，若会话为空，则跳转到登录页面
        SessionDto session = sessionService.getSession(sessionId);
        if (Objects.isNull(session)) {
            return this.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        if (Strings.isNotBlank(userId)) {
            String selectedUserId = userId.trim();
            boolean allowed = userService.listUserVos(session).stream()
                .anyMatch(u -> Objects.equals(String.valueOf(u.getId()), selectedUserId));
            if (!allowed) {
                return redirectOAuthError(
                    clientId,
                    redirectUri,
                    AuthorizationState.resolveCallbackState(state),
                    "所选用户不属于当前登录账号"
                );
            }
        }

        // 生成授权码（会话含 IdP 信息时视为外部身份源授权链路）
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

    /**
     * 匿名 OAuth 授权：跳过登录/会话，直接发码并重定向到客户端回调地址。
     */
    public ModelAndView redirectAnonymousCallback(String clientId, String redirectUri, String state) {
        IamAccessProperties.AnonymousAuth anonymous = iamAccessProperties.getAnonymous();
        if (!anonymous.isConfigured()) {
            return redirectOAuthError(
                clientId,
                redirectUri,
                AuthorizationState.resolveCallbackState(state),
                IamErrorCode.ANONYMOUS_AUTH_DISABLED.getMessage()
            );
        }

        String code = authorizationService.generateAnonymousAuthorizationCode(
            clientId,
            anonymous.getOrganId(),
            anonymous.getRoleIds()
        );

        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam(Constants.CODE, code);

        String callbackState = AuthorizationState.resolveCallbackState(state);
        if (Strings.isNotBlank(callbackState)) {
            redirectUrl.queryParam(Constants.STATE, callbackState);
        }

        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }

    /**
     * 由 IAM 拼装 consent 预览：查应用名称/描述，并用所选用户的 organId 解析租户名。
     */
    private void populatePreview(ModelMap model, UserVo selectedUser, String applicationCode) {
        try {
            ApplicationSelectDto selectDto = new ApplicationSelectDto();
            selectDto.setApplicationCode(applicationCode);
            Result<List<ApplicationVo>> appResult = applicationClient.selectList(selectDto);
            if (!appResult.isSuccess() || appResult.getData() == null || appResult.getData().isEmpty()) {
                model.addAttribute("previewError",
                    appResult.getErrorMessage() == null ? "无法加载应用信息" : appResult.getErrorMessage());
                return;
            }
            ApplicationVo application = appResult.getData().getFirst();

            ConsentPreviewDto preview = new ConsentPreviewDto();
            preview.setApplicationName(application.getApplicationName());
            preview.setDescription(application.getDescription());
            preview.setOrganName(resolveOrganName(selectedUser == null ? null : selectedUser.getOrganId()));
            model.addAttribute("preview", preview);
        } catch (Exception ex) {
            model.addAttribute("previewError", "无法加载应用信息，请稍后重试");
        }
    }

    /**
     * 通过 Basis {@code /organ/id_name_map} 解析机构名称；失败时返回空串（模板可隐藏）。
     */
    private String resolveOrganName(Long organId) {
        if (organId == null) {
            return "";
        }
        try {
            OrganIdNameMapSelectDto dto = new OrganIdNameMapSelectDto();
            dto.setIds(Set.of(organId));
            Result<List<OrganIdNameVo>> result = organClient.selectOrganIdNameMap(dto);
            if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
                String name = result.getData().getFirst().getOrganName();
                return name == null ? "" : name;
            }
        } catch (Exception ignored) {
            // 预览降级：机构名缺失不阻断确认页
        }
        return "";
    }

    /**
     * 向客户端回调地址重定向 OAuth 标准 {@code error} 参数。
     */
    private ModelAndView redirectClientError(
        String clientId, String redirectUri, String state, String error) {
        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam(Constants.CLIENT_ID, clientId)
            .queryParam("error", error);
        if (Strings.isNotBlank(state)) {
            redirectUrl.queryParam(Constants.STATE, state);
        }
        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }
}
