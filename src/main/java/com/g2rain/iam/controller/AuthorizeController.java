package com.g2rain.iam.controller;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.utils.AuthorizationState;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * 授权控制器：全参数建 tid，或仅 tid 续跑。
 */
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth")
public class AuthorizeController {

    private final ModelAndViewService modelAndViewService;
    private final AuthorizationTransactionService transactionService;
    private final AuthFlowCookieService authFlowCookieService;
    private final AuthorizationFlowService authorizationFlowService;

    @GetMapping(value = "/authorize")
    @PostMapping(value = "/authorize")
    public ModelAndView authorize(
        HttpServletRequest request,
        HttpServletResponse response,
        @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
        @RequestParam(name = Constants.TID, required = false) String tid,
        @RequestParam(name = "clientId", required = false) String clientId,
        @RequestParam(name = "redirectUri", required = false) String redirectUri,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "applicationCode", required = false) String applicationCode,
        @RequestParam(name = "userId", required = false) String userId) {

        if (Strings.isNotBlank(tid)) {
            return continueWithTid(request, sessionId, tid.trim(), userId);
        }

        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            return modelAndViewService.redirectError(clientId, redirectUri, state);
        }

        try {
            String flowHash = authFlowCookieService.ensureAndBind(request, response);
            AuthorizationTransactionDto txn = transactionService.createOrReuse(
                clientId, redirectUri, state, applicationCode, flowHash);
            String prg = UriComponentsBuilder.fromPath("/auth/authorize")
                .queryParam(Constants.TID, txn.getTid())
                .build()
                .toUriString();
            return new ModelAndView(Constants.REDIRECT + prg);
        } catch (BusinessException ex) {
            return modelAndViewService.redirectOAuthError(
                clientId, redirectUri, AuthorizationState.resolveCallbackState(state), ex.getMessage());
        }
    }

    @PostMapping(value = "/authorize_selected")
    public ModelAndView authorizeSelected(
        HttpServletRequest request,
        @CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
        @RequestParam(name = Constants.TID, required = false) String tid,
        @RequestParam(name = "userId", required = false) String userId,
        @RequestParam(name = "denied", defaultValue = "false") boolean denied) {

        if (Strings.isBlank(tid)) {
            return authorizationFlowService.renderFlowError(
                null, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
        }

        try {
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireActive(tid.trim(), flowHash);
            if (txn.getStatus() == AuthorizationTransactionStatus.CONSENT_REQUIRED
                || txn.getStatus() == AuthorizationTransactionStatus.ACTIVATING) {
                return authorizationFlowService.confirmApplication(txn, sessionId, denied);
            }
            return authorizationFlowService.confirm(txn, sessionId, userId, denied);
        } catch (BusinessException ex) {
            return authorizationFlowService.renderFlowError(null, ex.getMessage());
        }
    }

    private ModelAndView continueWithTid(
        HttpServletRequest request, String sessionId, String tid, String userId) {
        try {
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireReadable(tid, flowHash);
            if (txn.getStatus() != null && txn.getStatus().isTerminal()) {
                return authorizationFlowService.continueFlow(txn, sessionId);
            }
            txn = transactionService.requireActive(tid, flowHash);
            if (Strings.isNotBlank(userId)
                && (txn.getStatus() == AuthorizationTransactionStatus.AUTHENTICATED
                || txn.getStatus() == AuthorizationTransactionStatus.CONSENT_REQUIRED)) {
                return authorizationFlowService.confirm(txn, sessionId, userId, false);
            }
            return authorizationFlowService.continueFlow(txn, sessionId);
        } catch (BusinessException ex) {
            return authorizationFlowService.renderFlowError(null, ex.getMessage());
        }
    }
}
