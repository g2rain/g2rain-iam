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
 * 授权控制器，处理基于授权事务（{@code tid}）的 OAuth 授权请求。
 * <p>
 * 首次携带完整 OAuth 参数时创建短期授权事务并 PRG 到仅含 {@code tid} 的 URL；
 * 后续请求只认 {@code tid}，并结合 flow Cookie 校验后按事务状态续跑。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 首次进入（全参数建 tid）
 * GET /auth/authorize?clientId=client123&redirectUri=http://example.com/callback&state=xyz
 * // 续跑（仅 tid）
 * GET /auth/authorize?tid=...
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth")
public class AuthorizeController {

    /**
     * 处理 {@link ModelAndView} 的创建与重定向（错误页、OAuth 错误回调等）。
     */
    private final ModelAndViewService modelAndViewService;

    /**
     * 授权事务 Redis 存储与状态迁移。
     */
    private final AuthorizationTransactionService transactionService;

    /**
     * 授权流程 Cookie，用于绑定 {@code tid} 与当前浏览器。
     */
    private final AuthFlowCookieService authFlowCookieService;

    /**
     * 基于 tid 的授权流程编排（登录分流、发码、拒绝回调等）。
     */
    private final AuthorizationFlowService authorizationFlowService;

    /**
     * 授权码请求入口：全参数建事务，或仅 {@code tid} 续跑。
     * <p>
     * 同时携带全参数与 {@code tid} 时以 {@code tid} 为准，忽略全参数，防止改写已冻结上下文。
     * 缺失必要参数且无有效 {@code tid} 时返回错误页。
     * </p>
     *
     * @param request         当前 HTTP 请求（读取 flow Cookie）
     * @param response        当前 HTTP 响应（签发 flow Cookie）
     * @param sessionId       当前用户会话 ID（Cookie，可选）
     * @param tid             授权事务 ID（续跑时必填）
     * @param clientId        客户端 ID（首次进入必填）
     * @param redirectUri     授权后重定向 URI（首次进入必填）
     * @param state           状态参数，通常用于防 CSRF
     * @param applicationCode 目标应用编码（可选）
     * @param userId          预选用户 ID（可选，续跑时可用于直接确认）
     * @return PRG 到仅含 tid 的授权页、流程续跑视图或错误页
     */
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

    /**
     * 用户确认或拒绝授权（POST {@code /authorize_selected}）。
     * <p>
     * 须携带有效 {@code tid}；应用授权 consent / 开通阶段走 {@link AuthorizationFlowService#confirmApplication}，
     * 其余走 {@link AuthorizationFlowService#confirm}。拒绝时向客户端回调 {@code error=access_denied}。
     * </p>
     *
     * @param request   当前 HTTP 请求（校验 flow Cookie）
     * @param sessionId 当前用户会话 ID
     * @param tid       授权事务 ID
     * @param userId    用户选择的用户 ID（无 applicationCode 流程时使用）
     * @param denied    为 {@code true} 表示用户拒绝授权
     * @return 客户端回调重定向、consent 续跑视图或错误页
     */
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

    /**
     * 仅凭 {@code tid} 续跑授权事务：终态幂等回读，或按当前状态继续编排。
     *
     * @param request   当前 HTTP 请求
     * @param sessionId 当前用户会话 ID
     * @param tid       授权事务 ID
     * @param userId    预选用户 ID（可选）
     * @return 流程续跑视图、发码回调或错误页
     */
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
