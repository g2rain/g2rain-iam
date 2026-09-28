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

/**
 * 钉钉 OAuth 控制器。
 * <p>
 * 路径前缀 {@code /auth/dingtalk}。登录链路须在授权事务（{@code tid}）内发起，
 * OAuth 上下文从事务读取，不在页面间透传完整 OAuth 参数。
 * </p>
 *
 * @author Alpha
 */
@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth/dingtalk")
@Tag(name = "钉钉 OAuth", description = "钉钉 OAuth相关接口")
public class DingTalkOAuthController {

    /**
     * 内嵌扫码引导服务。
     */
    private final DingTalkQrBootstrapService dingTalkQrBootstrapService;

    /**
     * 钉钉 OAuth 授权与回调登录服务。
     */
    private final DingTalkOAuthService dingTalkOAuthService;

    /**
     * 钉钉 Stream 授权码签发服务。
     */
    private final DingTalkStreamAuthorizationService dingTalkStreamAuthorizationService;

    /**
     * IAM 会话 Cookie 写入。
     */
    private final IamSessionCookieService iamSessionCookieService;

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
     * 申请内嵌扫码（方式二）的 sns 授权 goto URL。
     *
     * @param request 当前 HTTP 请求（校验 flow Cookie）
     * @param dto     内嵌扫码引导请求（须含 tid）
     * @return 包含 goto URL 的视图对象
     */
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

    /**
     * 跳转钉钉授权页：从授权事务读取 OAuth 上下文并重定向至钉钉。
     *
     * @param request   当前 HTTP 请求
     * @param bindMode  绑定模式（内部企业 / 第三方等）
     * @param tid       授权事务 ID
     * @param loginRole 登录角色（可选，如管理员开户）
     * @return 重定向至钉钉授权页或错误页
     */
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

    /**
     * 钉钉授权回调：换票建会话后回到授权事务继续编排。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应（写入会话 Cookie）
     * @param code        钉钉授权码
     * @param opaqueState 不透明 state（Redis 中关联 tid 与 OAuth 上下文）
     * @return 授权流程续跑视图或登录/错误页
     */
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

    /**
     * 回调失败时尽量回到同一事务的登录页回显错误。
     *
     * @param request      当前 HTTP 请求
     * @param opaqueState  不透明 state
     * @param errorMessage 错误信息
     * @return 登录页或流程错误页
     */
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

    /**
     * 钉钉 Stream 场景签发短时授权码。
     *
     * @param dto Stream 授权请求
     * @return 授权码视图对象
     */
    @ResponseBody
    @PostMapping("/authorize_code")
    public Result<DingTalkStreamAuthorizationVo> authorizeCode(@Valid @RequestBody DingTalkStreamAuthorizationDto dto) {
        return Result.success(dingTalkStreamAuthorizationService.issueStreamAuthorizationCode(dto));
    }

    /**
     * 从授权事务解析 OAuth 上下文（clientId / redirectUri / state / applicationCode）。
     *
     * @param request 当前 HTTP 请求
     * @param tid     授权事务 ID
     * @return 解析后的 OAuth 上下文
     */
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
