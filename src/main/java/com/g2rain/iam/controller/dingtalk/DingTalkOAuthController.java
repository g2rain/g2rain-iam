package com.g2rain.iam.controller.dingtalk;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dto.DingTalkQrBootstrapDto;
import com.g2rain.iam.dto.DingTalkStreamAuthorizationDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.DingTalkOAuthService;
import com.g2rain.iam.service.DingTalkQrBootstrapService;
import com.g2rain.iam.service.DingTalkStreamAuthorizationService;
import com.g2rain.iam.service.IamSessionCookieService;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import com.g2rain.iam.vo.DingTalkStreamAuthorizationVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth/dingtalk")
@Tag(name = "钉钉 OAuth", description = "钉钉 OAuth相关接口")
public class DingTalkOAuthController {

    private final DingTalkQrBootstrapService dingTalkQrBootstrapService;
    private final DingTalkOAuthService dingTalkOAuthService;
    private final DingTalkStreamAuthorizationService dingTalkStreamAuthorizationService;
    private final IamSessionCookieService iamSessionCookieService;
    private final AuthorizationTransactionService transactionService;
    private final AuthFlowCookieService authFlowCookieService;
    private final AuthorizationFlowService authorizationFlowService;

    @ResponseBody
    @PostMapping("/qr/bootstrap")
    @Operation(summary = "申请内嵌扫码", description = "申请内嵌扫码（方式二）的 sns 授权 goto URL")
    public Result<DingTalkQrBootstrapVo> qrBootstrap(
        HttpServletRequest request,
        @Valid @RequestBody DingTalkQrBootstrapDto dto) {
        ResolvedOAuth ctx = resolveOAuthContext(request, dto.getTid());
        authorizationFlowService.markIdpPending(transactionService.get(ctx.tid()));
        return Result.success(dingTalkQrBootstrapService.buildQrBootstrap(
            dto.getBindMode(),
            ctx.clientId(),
            ctx.redirectUri(),
            ctx.state(),
            dto.getLoginRole(),
            ctx.applicationCode(),
            ctx.tid()
        ));
    }

    @GetMapping("/authorize")
    @Operation(summary = "跳转钉钉授权页", hidden = true)
    @ApiResponse(responseCode = "302", description = "重定向至钉钉授权页或错误登录页")
    public ModelAndView authorize(
        HttpServletRequest request,
        @RequestParam(name = "bindMode") String bindMode,
        @RequestParam(name = Constants.TID, required = false) String tid,
        @RequestParam(name = "loginRole", required = false) String loginRole) {
        try {
            ResolvedOAuth ctx = resolveOAuthContext(request, tid);
            authorizationFlowService.markIdpPending(transactionService.get(ctx.tid()));
            String url = dingTalkOAuthService.buildDingTalkAuthorizeRedirectUrl(
                bindMode, ctx.clientId(), ctx.redirectUri(), ctx.state(), loginRole,
                ctx.applicationCode(), ctx.tid());
            return new ModelAndView(Constants.REDIRECT + url);
        } catch (Exception e) {
            log.error("钉钉授权跳转失败 bindMode={} message={}", bindMode, e.getMessage(), e);
            return authorizationFlowService.renderFlowError(null, "钉钉授权准备失败，请稍后重试或改用账号密码登录");
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "钉钉授权回调", hidden = true)
    public ModelAndView callback(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name = "code") String code,
        @RequestParam(name = "state") String opaqueState) {
        try {
            DingTalkOAuthResult result = dingTalkOAuthService.finishLogin(code, opaqueState);
            if (Strings.isBlank(result.transactionId())) {
                throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
            }
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireActive(result.transactionId(), flowHash);
            iamSessionCookieService.writeSessionCookie(response, result.sessionId());
            return authorizationFlowService.afterIdpLogin(txn, result.sessionId());
        } catch (Exception e) {
            log.error("钉钉登录回调处理失败 message={}", e.getMessage(), e);
            return dingTalkCallbackErrorView(request, opaqueState, "钉钉登录失败，请返回应用重新发起授权");
        }
    }

    private ModelAndView dingTalkCallbackErrorView(HttpServletRequest request, String opaqueState, String errorMessage) {
        Optional<DingTalkOAuthStateDto> payloadOpt = dingTalkOAuthService.peekOAuthState(opaqueState);
        if (payloadOpt.isPresent()) {
            DingTalkOAuthStateDto payload = payloadOpt.get();
            if (Strings.isNotBlank(payload.getTransactionId())) {
                try {
                    String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
                    AuthorizationTransactionDto txn = transactionService.requireReadable(
                        payload.getTransactionId(), flowHash);
                    return authorizationFlowService.renderLogin(txn, errorMessage, null);
                } catch (BusinessException ex) {
                    return authorizationFlowService.renderFlowError(null, errorMessage);
                }
            }
        }
        return authorizationFlowService.renderFlowError(null, errorMessage);
    }

    @ResponseBody
    @PostMapping("/authorize_code")
    public Result<DingTalkStreamAuthorizationVo> authorizeCode(@Valid @RequestBody DingTalkStreamAuthorizationDto dto) {
        return Result.success(dingTalkStreamAuthorizationService.issueStreamAuthorizationCode(dto));
    }

    private ResolvedOAuth resolveOAuthContext(HttpServletRequest request, String tid) {
        if (Strings.isBlank(tid)) {
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
        }
        String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
        AuthorizationTransactionDto txn = transactionService.requireActive(tid.trim(), flowHash);
        return new ResolvedOAuth(
            txn.getTid(),
            txn.getClientId(),
            txn.getRedirectUri(),
            txn.getState(),
            txn.getApplicationCode()
        );
    }

    private record ResolvedOAuth(
        String tid, String clientId, String redirectUri, String state, String applicationCode) {
    }
}
