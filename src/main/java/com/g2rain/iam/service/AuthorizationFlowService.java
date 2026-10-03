package com.g2rain.iam.service;

import com.g2rain.basis.dto.ApplicationAuthorizationActivateSelfRequest;
import com.g2rain.basis.dto.ApplicationSelectDto;
import com.g2rain.basis.dto.OrganIdNameMapSelectDto;
import com.g2rain.basis.enums.ApplicationType;
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
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.ConsentPreviewDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.AuthorizationMode;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.LoginMethod;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于授权事务（{@code tid}）的流程编排服务。
 * <p>
 * 按事务状态分流至登录、选用户、应用授权 consent、发码或拒绝回调；
 * 页面间不再透传完整 OAuth 参数，上下文冻结在 {@link AuthorizationTransactionDto} 中。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationFlowService {

    private final AuthorizationTransactionService transactionService;
    private final SessionService sessionService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final IamAccessProperties iamAccessProperties;
    private final ApplicationAuthorizationClient applicationAuthorizationClient;
    private final ApplicationClient applicationClient;
    private final OrganClient organClient;

    /**
     * 按事务当前状态继续编排：匿名发码、登录、选用户、consent 或终态幂等回读。
     *
     * @param txn       授权事务
     * @param sessionId 当前浏览器会话 ID（可选）
     * @return 下一页面视图或客户端回调重定向
     */
    public ModelAndView continueFlow(AuthorizationTransactionDto txn, String sessionId) {
        if (txn.getStatus() != null && txn.getStatus().isTerminal()) {
            return terminalResult(txn);
        }
        if (txn.getAuthorizationMode() == AuthorizationMode.ANONYMOUS) {
            return completeAnonymous(txn);
        }

        AuthorizationTransactionStatus status = txn.getStatus();
        if (status == AuthorizationTransactionStatus.CREATED
            || status == AuthorizationTransactionStatus.IDP_PENDING) {
            if (Strings.isBlank(sessionId) || sessionService.isSessionExpired(sessionId)) {
                return renderLogin(txn, null, null);
            }
            bindSessionAndAuthenticate(txn, sessionId);
            txn = transactionService.get(txn.getTid());
        }

        if (txn.getStatus() == AuthorizationTransactionStatus.AUTHENTICATED
            || txn.getStatus() == AuthorizationTransactionStatus.USER_SELECTED
            || txn.getStatus() == AuthorizationTransactionStatus.CONSENT_REQUIRED
            || txn.getStatus() == AuthorizationTransactionStatus.ACTIVATING) {
            return progressAfterAuthenticated(txn, sessionId, null);
        }

        return renderFlowError(txn, IamErrorCode.AUTH_TRANSACTION_STATE_INVALID.getMessage());
    }

    /**
     * 账号密码登录成功后：将事务推进为 {@link AuthorizationTransactionStatus#AUTHENTICATED} 并继续编排。
     *
     * @param txn       授权事务
     * @param sessionId 新建立的会话 ID
     * @return 选用户页、consent 页或发码回调
     */
    public ModelAndView afterPasswordLogin(AuthorizationTransactionDto txn, String sessionId) {
        if (!transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CREATED,
            AuthorizationTransactionStatus.AUTHENTICATED,
            d -> d.setSessionId(sessionId))
            && !transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.IDP_PENDING,
            AuthorizationTransactionStatus.AUTHENTICATED,
            d -> d.setSessionId(sessionId))) {
            AuthorizationTransactionDto current = transactionService.get(txn.getTid());
            if (current != null && current.getStatus() == AuthorizationTransactionStatus.AUTHENTICATED) {
                return progressAfterAuthenticated(current, sessionId, null);
            }
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_STATE_INVALID);
        }
        AuthorizationTransactionDto updated = transactionService.get(txn.getTid());
        return progressAfterAuthenticated(updated, sessionId, null);
    }

    /**
     * 渲染登录页，仅向模型注入 {@code tid} 与可选错误/用户名回显，以及 IdP 入口配置。
     *
     * @param txn      授权事务
     * @param error    错误信息（可选）
     * @param username 用户名回显（可选）
     * @return 登录页视图
     */
    public ModelAndView renderLogin(AuthorizationTransactionDto txn, String error, String username) {
        ModelAndView mv = new ModelAndView("login");
        ModelMap model = mv.getModelMap();
        model.addAttribute(Constants.TID, txn.getTid());
        if (Strings.isNotBlank(error)) {
            model.addAttribute("error", error);
        }
        if (Strings.isNotBlank(username)) {
            model.addAttribute("username", username);
        }
        AuthPolicySnapshot policy = txn.getAuthPolicy();
        boolean hasPassword = policy != null && policy.allows(LoginMethod.PASSWORD);
        boolean allowRegister = policy != null && policy.isAllowRegister();
        model.addAttribute("hasPassword", hasPassword);
        model.addAttribute("allowRegister", allowRegister);
        if (policy != null && policy.allows(LoginMethod.DINGTALK)
            && Strings.isNotBlank(policy.getDingTalkBindMode())) {
            model.addAttribute("dingTalkBindMode", policy.getDingTalkBindMode());
        }
        if (policy != null && policy.allows(LoginMethod.WECOM)
            && Strings.isNotBlank(policy.getWeComBindMode())) {
            model.addAttribute("weComBindMode", policy.getWeComBindMode());
        }
        return mv;
    }

    /**
     * 渲染注册页，仅向模型注入 {@code tid}。
     *
     * @param txn 授权事务
     * @return 注册页视图
     */
    public ModelAndView renderRegister(AuthorizationTransactionDto txn) {
        AuthPolicySnapshot policy = txn.getAuthPolicy();
        if (policy == null || !policy.isAllowRegister()) {
            return renderFlowError(txn, IamErrorCode.AUTH_POLICY_REGISTER_DENIED.getMessage());
        }
        ModelAndView mv = new ModelAndView("register");
        ModelMap model = mv.getModelMap();
        model.addAttribute(Constants.TID, txn.getTid());
        return mv;
    }

    /**
     * 用户确认选中身份或拒绝授权。
     * <p>
     * 拒绝时标记 {@code DENIED} 并向客户端回调 {@code access_denied}；
     * 确认时校验用户属于当前会话；仅 {@code openPlatformConsent=true} 进入 consent，否则直接发码。
     * </p>
     *
     * @param txn       授权事务
     * @param sessionId 当前会话 ID
     * @param userId    所选用户 ID
     * @param denied    是否拒绝授权
     * @return 发码回调、consent 页、登录页或错误页
     */
    public ModelAndView confirm(AuthorizationTransactionDto txn, String sessionId, String userId, boolean denied) {
        if (denied) {
            if (!transactionService.compareAndUpdate(
                txn.getTid(),
                txn.getStatus(),
                AuthorizationTransactionStatus.DENIED,
                null)) {
                AuthorizationTransactionDto current = transactionService.requireReadable(txn.getTid(), txn.getFlowCookieHash());
                return terminalResult(current);
            }
            AuthorizationTransactionDto deniedTxn = transactionService.get(txn.getTid());
            return redirectClientError(deniedTxn, "access_denied");
        }

        SessionDto session = sessionService.getSession(sessionId);
        if (session == null) {
            return renderLogin(txn, null, null);
        }

        if (Strings.isBlank(userId)) {
            return renderFlowError(txn, SystemErrorCode.PARAM_REQUIRED.getMessage("userId"));
        }
        String selectedUserId = userId.trim();
        List<UserVo> users = userService.listUserVos(session);
        boolean allowed = users.stream()
            .anyMatch(u -> Objects.equals(String.valueOf(u.getId()), selectedUserId));
        if (!allowed) {
            return renderFlowError(txn, "所选用户不属于当前登录账号");
        }

        ModelAndView resolveError = ensureApplicationConsentResolved(txn);
        if (resolveError != null) {
            return resolveError;
        }
        AuthorizationTransactionDto resolved = transactionService.get(txn.getTid());

        transactionService.compareAndUpdate(
            resolved.getTid(),
            resolved.getStatus(),
            AuthorizationTransactionStatus.USER_SELECTED,
            d -> d.setSelectedUserId(selectedUserId));
        AuthorizationTransactionDto selected = transactionService.get(txn.getTid());

        if (!requiresOpenPlatformConsent(selected)) {
            return completeWithCode(selected, session, selectedUserId);
        }

        transactionService.compareAndUpdate(
            selected.getTid(),
            AuthorizationTransactionStatus.USER_SELECTED,
            AuthorizationTransactionStatus.CONSENT_REQUIRED,
            null);
        return progressAfterAuthenticated(transactionService.get(txn.getTid()), sessionId, selectedUserId);
    }

    /**
     * 应用授权确认：调用 Basis {@code activate_self} 开通 SELF 应用后发码。
     * <p>
     * 开通失败回滚至 {@code CONSENT_REQUIRED} 并在 consent 页展示错误；拒绝时复用 {@link #confirm}。
     * 仅允许 {@code openPlatformConsent=true} 的事务进入。
     * </p>
     *
     * @param txn       授权事务（须含 applicationCode、openPlatformConsent 与 selectedUserId）
     * @param sessionId 当前会话 ID
     * @param denied    是否拒绝授权
     * @return 发码回调、consent 错误回显或拒绝回调
     */
    public ModelAndView confirmApplication(AuthorizationTransactionDto txn, String sessionId, boolean denied) {
        if (denied) {
            return confirm(txn, sessionId, txn.getSelectedUserId(), true);
        }

        ModelAndView resolveError = ensureApplicationConsentResolved(txn);
        if (resolveError != null) {
            return resolveError;
        }
        txn = transactionService.get(txn.getTid());
        if (!requiresOpenPlatformConsent(txn)) {
            return renderFlowError(txn, IamErrorCode.AUTH_TRANSACTION_STATE_INVALID.getMessage());
        }

        SessionDto session = sessionService.getSession(sessionId);
        if (session == null) {
            return renderLogin(txn, null, null);
        }
        String selectedUserId = txn.getSelectedUserId();
        if (Strings.isBlank(selectedUserId)) {
            return renderFlowError(txn, SystemErrorCode.PARAM_REQUIRED.getMessage("userId"));
        }

        String operationId = Strings.isNotBlank(txn.getActivationOperationId())
            ? txn.getActivationOperationId()
            : IamUtils.generateAuthorizationCode();
        long leaseUntil = Instant.now().getEpochSecond()
            + iamAccessProperties.getAuthorizationTransaction().getActivationLeaseSeconds();

        if (!transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CONSENT_REQUIRED,
            AuthorizationTransactionStatus.ACTIVATING,
            d -> {
                d.setActivationOperationId(operationId);
                d.setActivationLeaseUntil(leaseUntil);
            })
            && txn.getStatus() != AuthorizationTransactionStatus.ACTIVATING) {
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_STATE_INVALID);
        }

        Long parsedUserId;
        try {
            parsedUserId = Long.valueOf(selectedUserId);
        } catch (NumberFormatException ex) {
            rollbackToConsent(txn.getTid());
            return renderFlowError(txn, SystemErrorCode.PARAM_VAL_INVALID.getMessage("userId"));
        }

        ApplicationAuthorizationActivateSelfRequest request = new ApplicationAuthorizationActivateSelfRequest();
        request.setApplicationCode(txn.getApplicationCode());
        request.setUserId(parsedUserId);

        Result<ApplicationAuthorizationActivateSelfVo> result;
        try {
            result = applicationAuthorizationClient.activateSelf(request);
        } catch (Exception ex) {
            rollbackToConsent(txn.getTid());
            return renderConsentWithError(transactionService.get(txn.getTid()), session, "开通应用失败，请稍后重试");
        }
        if (!result.isSuccess() || result.getData() == null) {
            rollbackToConsent(txn.getTid());
            String message = result.getErrorMessage();
            return renderConsentWithError(
                transactionService.get(txn.getTid()),
                session,
                Strings.isBlank(message) ? "开通应用失败" : message);
        }

        ApplicationAuthorizationActivateSelfVo activated = result.getData();
        boolean thirdPartyIdpLogin = Strings.isNotBlank(session.getIdpType());
        String code = authorizationService.generateAuthorizationCode(
            session,
            txn.getClientId(),
            selectedUserId,
            thirdPartyIdpLogin,
            txn.getApplicationCode(),
            activated.getApplicationId(),
            activated.getOrganId()
        );
        if (!transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.ACTIVATING,
            AuthorizationTransactionStatus.COMPLETED,
            d -> d.setIssuedCode(code))) {
            AuthorizationTransactionDto current = transactionService.get(txn.getTid());
            return terminalResult(current);
        }
        return redirectWithCode(transactionService.get(txn.getTid()), code);
    }

    /**
     * 将事务标记为 IdP 登录进行中（{@code CREATED → IDP_PENDING}）。
     *
     * @param txn 授权事务
     * @return 恒为 {@code null}（调用方自行决定后续跳转）
     */
    public ModelAndView markIdpPending(AuthorizationTransactionDto txn) {
        transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CREATED,
            AuthorizationTransactionStatus.IDP_PENDING,
            null);
        return null;
    }

    /**
     * IdP 登录成功后的编排入口，语义同 {@link #afterPasswordLogin}。
     *
     * @param txn       授权事务
     * @param sessionId 新建立的会话 ID
     * @return 选用户页、consent 页或发码回调
     */
    public ModelAndView afterIdpLogin(AuthorizationTransactionDto txn, String sessionId) {
        return afterPasswordLogin(txn, sessionId);
    }

    /**
     * 渲染流程错误页（不自动外跳）。
     *
     * @param txn     授权事务（可为 null）
     * @param message 安全可展示的错误信息
     * @return 错误页视图
     */
    public ModelAndView renderFlowError(AuthorizationTransactionDto txn, String message) {
        ModelAndView mv = new ModelAndView("error");
        ModelMap model = mv.getModelMap();
        model.addAttribute("error", message);
        model.addAttribute("redirectUri", "");
        model.addAttribute("state", "");
        model.addAttribute(Constants.TID, txn == null ? "" : txn.getTid());
        return mv;
    }

    private void bindSessionAndAuthenticate(AuthorizationTransactionDto txn, String sessionId) {
        transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CREATED,
            AuthorizationTransactionStatus.AUTHENTICATED,
            d -> d.setSessionId(sessionId));
        transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.IDP_PENDING,
            AuthorizationTransactionStatus.AUTHENTICATED,
            d -> d.setSessionId(sessionId));
    }

    private ModelAndView progressAfterAuthenticated(
        AuthorizationTransactionDto txn, String sessionId, String preferredUserId) {
        SessionDto session = sessionService.getSession(
            Strings.isNotBlank(sessionId) ? sessionId : txn.getSessionId());
        if (session == null) {
            return renderLogin(txn, null, null);
        }

        ModelAndView resolveError = ensureApplicationConsentResolved(txn);
        if (resolveError != null) {
            return resolveError;
        }
        txn = transactionService.get(txn.getTid());

        List<UserVo> users = userService.listUserVos(session);
        if (users.isEmpty()) {
            transactionService.compareAndUpdate(
                txn.getTid(),
                txn.getStatus(),
                AuthorizationTransactionStatus.FAILED,
                null);
            return renderFlowError(txn, "当前账号没有可用用户");
        }

        String selected = Strings.isNotBlank(preferredUserId)
            ? preferredUserId
            : (Strings.isNotBlank(txn.getSelectedUserId()) ? txn.getSelectedUserId() : null);
        if (Strings.isBlank(selected) && users.size() == 1) {
            selected = String.valueOf(users.getFirst().getId());
        }
        final String resolvedSelected = selected;

        if (!requiresOpenPlatformConsent(txn)) {
            if (Strings.isNotBlank(resolvedSelected)) {
                transactionService.compareAndUpdate(
                    txn.getTid(),
                    txn.getStatus(),
                    AuthorizationTransactionStatus.USER_SELECTED,
                    d -> d.setSelectedUserId(resolvedSelected));
                return completeWithCode(transactionService.get(txn.getTid()), session, resolvedSelected);
            }
            return buildConsentView(txn, users, null, false);
        }

        if (Strings.isNotBlank(resolvedSelected)) {
            if (txn.getStatus() != AuthorizationTransactionStatus.CONSENT_REQUIRED
                && txn.getStatus() != AuthorizationTransactionStatus.ACTIVATING) {
                transactionService.compareAndUpdate(
                    txn.getTid(),
                    txn.getStatus(),
                    AuthorizationTransactionStatus.CONSENT_REQUIRED,
                    d -> d.setSelectedUserId(resolvedSelected));
            }
            AuthorizationTransactionDto consentTxn = transactionService.get(txn.getTid());
            UserVo selectedUser = users.stream()
                .filter(u -> Objects.equals(String.valueOf(u.getId()), resolvedSelected))
                .findFirst()
                .orElse(null);
            return buildConsentView(consentTxn, users, selectedUser, true);
        }

        return buildConsentView(txn, users, null, true);
    }

    private ModelAndView completeAnonymous(AuthorizationTransactionDto txn) {
        IamAccessProperties.AnonymousAuth anonymous = iamAccessProperties.getAnonymous();
        if (!anonymous.isConfigured()) {
            return renderFlowError(txn, IamErrorCode.ANONYMOUS_AUTH_DISABLED.getMessage());
        }
        String code = authorizationService.generateAnonymousAuthorizationCode(
            txn.getClientId(), anonymous.getOrganId(), anonymous.getRoleIds());
        if (!transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CREATED,
            AuthorizationTransactionStatus.COMPLETED,
            d -> d.setIssuedCode(code))) {
            return terminalResult(transactionService.get(txn.getTid()));
        }
        return redirectWithCode(transactionService.get(txn.getTid()), code);
    }

    private ModelAndView completeWithCode(AuthorizationTransactionDto txn, SessionDto session, String userId) {
        boolean thirdPartyIdpLogin = Strings.isNotBlank(session.getIdpType());
        String code = authorizationService.generateAuthorizationCode(
            session, txn.getClientId(), userId, thirdPartyIdpLogin);
        if (!transactionService.compareAndUpdate(
            txn.getTid(),
            txn.getStatus(),
            AuthorizationTransactionStatus.COMPLETED,
            d -> {
                d.setSelectedUserId(userId);
                d.setIssuedCode(code);
            })) {
            return terminalResult(transactionService.get(txn.getTid()));
        }
        return redirectWithCode(transactionService.get(txn.getTid()), code);
    }

    private ModelAndView terminalResult(AuthorizationTransactionDto txn) {
        if (txn == null) {
            return renderFlowError(null, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
        }
        if (txn.getStatus() == AuthorizationTransactionStatus.COMPLETED && Strings.isNotBlank(txn.getIssuedCode())) {
            return redirectWithCode(txn, txn.getIssuedCode());
        }
        if (txn.getStatus() == AuthorizationTransactionStatus.DENIED) {
            return redirectClientError(txn, "access_denied");
        }
        return renderFlowError(txn, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
    }

    private ModelAndView redirectWithCode(AuthorizationTransactionDto txn, String code) {
        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(txn.getRedirectUri())
            .queryParam(Constants.CLIENT_ID, txn.getClientId())
            .queryParam(Constants.CODE, code);
        if (Strings.isNotBlank(txn.getState())) {
            redirectUrl.queryParam(Constants.STATE, txn.getState());
        }
        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }

    private ModelAndView redirectClientError(AuthorizationTransactionDto txn, String error) {
        UriComponentsBuilder redirectUrl = UriComponentsBuilder.fromUriString(txn.getRedirectUri())
            .queryParam(Constants.CLIENT_ID, txn.getClientId())
            .queryParam("error", error);
        if (Strings.isNotBlank(txn.getState())) {
            redirectUrl.queryParam(Constants.STATE, txn.getState());
        }
        return new ModelAndView(Constants.REDIRECT + redirectUrl.build().toUriString());
    }

    private ModelAndView buildConsentView(
        AuthorizationTransactionDto txn, List<UserVo> users, UserVo selectedUser, boolean withPreview) {
        ModelAndView mv = new ModelAndView("consent");
        ModelMap model = mv.getModelMap();
        model.addAttribute(Constants.TID, txn.getTid());
        model.addAttribute("users", users.stream()
            .map(u -> Map.of(
                "id", String.valueOf(u.getId()),
                "username", u.getRealName() == null ? "" : u.getRealName()
            ))
            .toList());
        boolean openPlatform = requiresOpenPlatformConsent(txn);
        model.addAttribute("applicationCode",
            openPlatform && Strings.isNotBlank(txn.getApplicationCode()) ? txn.getApplicationCode() : "");
        if (selectedUser != null) {
            model.addAttribute("selectedUserId", String.valueOf(selectedUser.getId()));
            if (withPreview && openPlatform && Strings.isNotBlank(txn.getApplicationCode())) {
                populatePreview(model, selectedUser, txn.getApplicationCode());
            }
        }
        return mv;
    }

    private ModelAndView renderConsentWithError(AuthorizationTransactionDto txn, SessionDto session, String error) {
        List<UserVo> users = userService.listUserVos(session);
        UserVo selected = users.stream()
            .filter(u -> Objects.equals(String.valueOf(u.getId()), txn.getSelectedUserId()))
            .findFirst()
            .orElse(null);
        ModelAndView mv = buildConsentView(txn, users, selected, true);
        mv.getModelMap().addAttribute("previewError", error);
        return mv;
    }

    private void rollbackToConsent(String tid) {
        transactionService.compareAndUpdate(
            tid,
            AuthorizationTransactionStatus.ACTIVATING,
            AuthorizationTransactionStatus.CONSENT_REQUIRED,
            null);
    }

    /**
     * 有 {@code applicationCode} 时查询 Basis 应用类型并冻结 {@code openPlatformConsent}。
     *
     * @param txn 授权事务
     * @return 解析失败时的错误页；成功或无需解析时返回 {@code null}
     */
    private ModelAndView ensureApplicationConsentResolved(AuthorizationTransactionDto txn) {
        if (Strings.isBlank(txn.getApplicationCode())) {
            return null;
        }
        if (txn.getOpenPlatformConsent() != null) {
            return null;
        }

        final boolean openPlatform;
        try {
            ApplicationSelectDto selectDto = new ApplicationSelectDto();
            selectDto.setApplicationCode(txn.getApplicationCode().trim());
            Result<List<ApplicationVo>> appResult = applicationClient.selectList(selectDto);
            if (!appResult.isSuccess() || appResult.getData() == null || appResult.getData().isEmpty()) {
                String message = appResult.getErrorMessage();
                return renderFlowError(txn, Strings.isBlank(message) ? "无法加载应用信息" : message);
            }
            ApplicationVo application = appResult.getData().getFirst();
            if (Strings.isBlank(application.getApplicationType())) {
                return renderFlowError(txn, "应用类型无效");
            }
            ApplicationType type = ApplicationType.fromName(application.getApplicationType().trim());
            openPlatform = ApplicationType.nonMicroApp(type);
        } catch (BusinessException ex) {
            return renderFlowError(txn, ex.getMessage() == null ? "应用类型无效" : ex.getMessage());
        } catch (Exception ex) {
            return renderFlowError(txn, "无法加载应用信息，请稍后重试");
        }

        AuthorizationTransactionDto current = transactionService.get(txn.getTid());
        if (current == null) {
            return renderFlowError(txn, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
        }
        if (current.getOpenPlatformConsent() != null) {
            return null;
        }
        if (!transactionService.compareAndUpdate(
            current.getTid(),
            current.getStatus(),
            current.getStatus(),
            d -> d.setOpenPlatformConsent(openPlatform))) {
            AuthorizationTransactionDto after = transactionService.get(txn.getTid());
            if (after != null && after.getOpenPlatformConsent() != null) {
                return null;
            }
            return renderFlowError(txn, IamErrorCode.AUTH_TRANSACTION_STATE_INVALID.getMessage());
        }
        return null;
    }

    private static boolean requiresOpenPlatformConsent(AuthorizationTransactionDto txn) {
        return Boolean.TRUE.equals(txn.getOpenPlatformConsent());
    }

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
            // preview degrade
        }
        return "";
    }
}
