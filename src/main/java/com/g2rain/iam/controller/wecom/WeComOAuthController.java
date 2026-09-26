package com.g2rain.iam.controller.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.WeComStreamAuthorizationDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.IamSessionCookieService;
import com.g2rain.iam.service.WeComOAuthService;
import com.g2rain.iam.service.WeComStreamAuthorizationService;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.vo.WeComStreamAuthorizationVo;
import com.g2rain.iam.wecom.WeComOAuthResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/auth/wecom")
@Tag(name = "企业微信 OAuth", description = "企业微信 OAuth 相关接口")
public class WeComOAuthController {
    private final WeComOAuthService oauthService;
    private final WeComStreamAuthorizationService streamAuthorizationService;
    private final IamSessionCookieService sessionCookieService;
    private final AuthorizationTransactionService transactionService;
    private final AuthFlowCookieService authFlowCookieService;
    private final AuthorizationFlowService authorizationFlowService;

    @GetMapping("/authorize")
    @Operation(summary = "跳转企业微信扫码登录", hidden = true)
    @ApiResponse(responseCode = "302", description = "重定向至企业微信扫码页")
    public ModelAndView authorize(
        HttpServletRequest request,
        @RequestParam String bindMode,
        @RequestParam(name = Constants.TID, required = false) String tid,
        @RequestParam(required = false) String loginRole) {
        try {
            ResolvedOAuth ctx = resolve(request, tid);
            authorizationFlowService.markIdpPending(transactionService.get(ctx.tid()));
            return new ModelAndView(Constants.REDIRECT
                + oauthService.buildAuthorizeUrl(
                bindMode, ctx.clientId(), ctx.redirectUri(), ctx.state(), loginRole,
                ctx.applicationCode(), ctx.tid()));
        } catch (Exception exception) {
            log.error("企业微信授权跳转失败 bindMode={}", bindMode, exception);
            return authorizationFlowService.renderFlowError(null, "企业微信授权准备失败，请稍后重试");
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "企业微信扫码登录回调", hidden = true)
    public ModelAndView callback(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name = "auth_code", required = false) String authCode,
        @RequestParam(name = "code", required = false) String code,
        @RequestParam String state) {
        String resolvedCode =
            authCode == null || authCode.isBlank() ? code : authCode;
        try {
            WeComOAuthResult result = oauthService.finishLogin(resolvedCode, state);
            if (Strings.isBlank(result.transactionId())) {
                throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
            }
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireActive(result.transactionId(), flowHash);
            sessionCookieService.writeSessionCookie(response, result.sessionId());
            return authorizationFlowService.afterIdpLogin(txn, result.sessionId());
        } catch (Exception exception) {
            log.error("企业微信扫码登录失败 stateLen={}",
                state == null ? 0 : state.length(), exception);
            return authorizationFlowService.renderFlowError(null, "企业微信登录失败，请返回应用重新发起授权");
        }
    }

    @ResponseBody
    @PostMapping("/authorize_code")
    public Result<WeComStreamAuthorizationVo> authorizeCode(
        @Valid @RequestBody WeComStreamAuthorizationDto dto) {
        return Result.success(streamAuthorizationService.issueStreamAuthorizationCode(dto));
    }

    private ResolvedOAuth resolve(HttpServletRequest request, String tid) {
        if (Strings.isBlank(tid)) {
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
        }
        String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
        AuthorizationTransactionDto txn = transactionService.requireActive(tid.trim(), flowHash);
        return new ResolvedOAuth(
            txn.getTid(), txn.getClientId(), txn.getRedirectUri(),
            txn.getState(), txn.getApplicationCode());
    }

    private record ResolvedOAuth(
        String tid, String clientId, String redirectUri, String state, String applicationCode) {
    }
}
