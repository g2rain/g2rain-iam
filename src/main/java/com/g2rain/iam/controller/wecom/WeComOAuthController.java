package com.g2rain.iam.controller.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.WeComStreamAuthorizationDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthPolicyGuard;
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

/**
 * 企业微信 OAuth 控制器。
 * <p>
 * 路径前缀 {@code /auth/wecom}。扫码登录须在授权事务（{@code tid}）内发起，
 * OAuth 上下文从事务读取，不在页面间透传完整 OAuth 参数。
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/auth/wecom")
@Tag(name = "企业微信 OAuth", description = "企业微信 OAuth 相关接口")
public class WeComOAuthController {

    /**
     * 企业微信 OAuth 授权与回调登录服务。
     */
    private final WeComOAuthService oauthService;

    /**
     * 企业微信 Stream 授权码签发服务。
     */
    private final WeComStreamAuthorizationService streamAuthorizationService;

    /**
     * IAM 会话 Cookie 写入。
     */
    private final IamSessionCookieService sessionCookieService;

    /**
     * 授权事务 Redis 存储与状态迁移。
     */
    private final AuthorizationTransactionService transactionService;

    /**
     * 授权流程 Cookie，用于绑定 {@code tid} 与当前浏览器。
     */
    private final AuthFlowCookieService authFlowCookieService;

    /**
     * 基于 tid 的授权流程编排。
     */
    private final AuthorizationFlowService authorizationFlowService;

    /**
     * 登录策略闸门。
     */
    private final AuthPolicyGuard authPolicyGuard;

    /**
     * 跳转企业微信扫码登录页：从授权事务读取 OAuth 上下文并重定向。
     *
     * @param request   当前 HTTP 请求
     * @param bindMode  绑定模式（内部企业 / 第三方等）
     * @param tid       授权事务 ID
     * @param loginRole 登录角色（可选）
     * @return 重定向至企业微信扫码页或错误页
     */
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
            AuthorizationTransactionDto txn = transactionService.get(ctx.tid());
            authPolicyGuard.requireWeCom(txn, bindMode);
            authorizationFlowService.markIdpPending(txn);
            return new ModelAndView(Constants.REDIRECT
                + oauthService.buildAuthorizeUrl(
                bindMode, ctx.clientId(), ctx.redirectUri(), ctx.state(), loginRole,
                ctx.applicationCode(), ctx.tid()));
        } catch (BusinessException exception) {
            return authorizationFlowService.renderFlowError(null, exception.getMessage());
        } catch (Exception exception) {
            log.error("企业微信授权跳转失败 bindMode={}", bindMode, exception);
            return authorizationFlowService.renderFlowError(null, "企业微信授权准备失败，请稍后重试");
        }
    }

    /**
     * 企业微信扫码登录回调：换票建会话后回到授权事务继续编排。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应（写入会话 Cookie）
     * @param authCode 企业微信授权码（优先）
     * @param code     兼容字段授权码
     * @param state    不透明 state（Redis 中关联 tid）
     * @return 授权流程续跑视图或错误页
     */
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

    /**
     * 企业微信 Stream 场景签发短时授权码。
     *
     * @param dto Stream 授权请求
     * @return 授权码视图对象
     */
    @ResponseBody
    @PostMapping("/authorize_code")
    public Result<WeComStreamAuthorizationVo> authorizeCode(
        @Valid @RequestBody WeComStreamAuthorizationDto dto) {
        return Result.success(streamAuthorizationService.issueStreamAuthorizationCode(dto));
    }

    /**
     * 从授权事务解析 OAuth 上下文。
     *
     * @param request 当前 HTTP 请求
     * @param tid     授权事务 ID
     * @return 解析后的 OAuth 上下文
     */
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

    /**
     * 从授权事务解析出的 OAuth 上下文快照。
     *
     * @param tid             授权事务 ID
     * @param clientId        客户端 ID
     * @param redirectUri     回调地址
     * @param state           业务 state
     * @param applicationCode 目标应用编码（可选）
     */
    private record ResolvedOAuth(
        String tid, String clientId, String redirectUri, String state, String applicationCode) {
    }
}
