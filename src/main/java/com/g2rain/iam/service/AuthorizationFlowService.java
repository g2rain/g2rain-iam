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
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.ConsentPreviewDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.AuthorizationMode;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import com.g2rain.iam.enums.IamErrorCode;
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
 * 基于 tid 的授权流程编排：按事务状态分流、发码、拒绝回调。
 */
@Service
@RequiredArgsConstructor
public class AuthorizationFlowService {

    private final AuthorizationTransactionService transactionService;
    private final SessionService sessionService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final IamAccessProperties iamAccessProperties;
    private final DingTalkIamProperties dingTalkIamProperties;
    private final WeComIamProperties weComIamProperties;
    private final ApplicationAuthorizationClient applicationAuthorizationClient;
    private final ApplicationClient applicationClient;
    private final OrganClient organClient;

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
        String dingTalk = dingTalkIamProperties.getLoginPageBindMode();
        if (Strings.isNotBlank(dingTalk)) {
            model.addAttribute("dingTalkBindMode", dingTalk.trim());
        }
        String weCom = weComIamProperties.getLoginPageBindMode();
        if (Strings.isNotBlank(weCom)) {
            model.addAttribute("weComBindMode", weCom.trim());
        }
        return mv;
    }

    public ModelAndView renderRegister(AuthorizationTransactionDto txn) {
        ModelAndView mv = new ModelAndView("register");
        ModelMap model = mv.getModelMap();
        model.addAttribute(Constants.TID, txn.getTid());
        return mv;
    }

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

        transactionService.compareAndUpdate(
            txn.getTid(),
            txn.getStatus(),
            AuthorizationTransactionStatus.USER_SELECTED,
            d -> d.setSelectedUserId(selectedUserId));
        AuthorizationTransactionDto selected = transactionService.get(txn.getTid());

        if (Strings.isBlank(selected.getApplicationCode())) {
            return completeWithCode(selected, session, selectedUserId);
        }

        transactionService.compareAndUpdate(
            selected.getTid(),
            AuthorizationTransactionStatus.USER_SELECTED,
            AuthorizationTransactionStatus.CONSENT_REQUIRED,
            null);
        return progressAfterAuthenticated(transactionService.get(txn.getTid()), sessionId, selectedUserId);
    }

    public ModelAndView confirmApplication(AuthorizationTransactionDto txn, String sessionId, boolean denied) {
        if (denied) {
            return confirm(txn, sessionId, txn.getSelectedUserId(), true);
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

    public ModelAndView markIdpPending(AuthorizationTransactionDto txn) {
        transactionService.compareAndUpdate(
            txn.getTid(),
            AuthorizationTransactionStatus.CREATED,
            AuthorizationTransactionStatus.IDP_PENDING,
            null);
        return null;
    }

    public ModelAndView afterIdpLogin(AuthorizationTransactionDto txn, String sessionId) {
        return afterPasswordLogin(txn, sessionId);
    }

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

        if (Strings.isBlank(txn.getApplicationCode())) {
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
        model.addAttribute("applicationCode", Strings.isBlank(txn.getApplicationCode()) ? "" : txn.getApplicationCode());
        if (selectedUser != null) {
            model.addAttribute("selectedUserId", String.valueOf(selectedUser.getId()));
            if (withPreview && Strings.isNotBlank(txn.getApplicationCode())) {
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
