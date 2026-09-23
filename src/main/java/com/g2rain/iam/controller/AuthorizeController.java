package com.g2rain.iam.controller;


import com.g2rain.common.utils.Strings;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.SessionService;
import com.g2rain.iam.utils.AuthorizationState;
import com.g2rain.iam.utils.Constants;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;


/**
 * 授权控制器，处理用户的授权请求。
 * <p>
 * 该控制器处理与授权相关的 HTTP 请求，包括授权码的生成、用户登录状态的验证等操作。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 GET 请求跳转到授权页
 * /auth/authorize?clientId=client123&redirectUri=http://example.com/callback
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
     * 会话服务，用于校验登录态与 OAuth consent 绑定。
     */
    private SessionService sessionService;

    /**
     * 处理 {@link ModelAndView} 的创建与重定向（登录页、选用户、应用授权 consent、回调等）。
     */
    private ModelAndViewService modelAndViewService;

    /**
     * 授权码请求入口：校验参数与会话后按是否携带 {@code applicationCode} 分流。
     * <p>
     * 未登录或会话失效则重定向登录页。已登录时：无 {@code applicationCode} 走原逻辑
     *（单用户直接发码、多用户先选）；有 {@code applicationCode} 进入应用授权 consent。
     * </p>
     *
     * @param sessionId       当前用户会话 ID
     * @param clientId        客户端 ID
     * @param redirectUri     授权后重定向 URI
     * @param state           状态参数，通常用于防 CSRF
     * @param applicationCode 目标应用编码（可选）
     * @param userId          预选用户 ID（可选）
     * @return 登录页、选用户页、consent 页、发码回调或错误页
     */
    @GetMapping(value = "/authorize")
    public ModelAndView authorize(@CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
                                  @RequestParam(name = "clientId", required = false) String clientId,
                                  @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                  @RequestParam(name = "state", required = false) String state,
                                  @RequestParam(name = "applicationCode", required = false) String applicationCode,
                                  @RequestParam(name = "userId", required = false) String userId) {

        // 检查 clientId 与 redirectUri，缺失则返回错误页
        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            return modelAndViewService.redirectError(clientId, redirectUri, state);
        }

        if (AuthorizationState.isAnonymous(state)) {
            return modelAndViewService.redirectAnonymousCallback(clientId, redirectUri, state);
        }

        // 未登录则跳转登录页
        if (Strings.isBlank(sessionId)) {
            return modelAndViewService.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        // 会话过期则重新登录
        if (sessionService.isSessionExpired(sessionId)) {
            return modelAndViewService.redirectLogin(clientId, redirectUri, state, applicationCode);
        }

        // 已登录：无 applicationCode 走原发码逻辑；有则进入应用授权 consent
        return modelAndViewService.redirectConsent(
            sessionId, clientId, redirectUri, state, applicationCode, userId);
    }

    /**
     * 用户确认或拒绝授权（POST {@code /authorize_selected}）。
     * <p>
     * 无 {@code applicationCode} 时走常规发码回调；有时先校验会话绑定、调用 Basis
     * {@code activate_self} 开通 SELF 应用后再发码。拒绝时向客户端回调 {@code error=access_denied}。
     * </p>
     *
     * @param sessionId       当前用户会话 ID
     * @param clientId        客户端 ID
     * @param redirectUri     授权后重定向 URI
     * @param state           请求的状态参数
     * @param userId          用户 ID
     * @param applicationCode 目标应用编码（可选）
     * @param denied          为 true 表示用户拒绝授权
     * @return 客户端回调重定向或错误页
     */
    @PostMapping(value = "/authorize_selected")
    public ModelAndView consent(@CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
                                @RequestParam(name = "clientId") String clientId,
                                @RequestParam(name = "redirectUri") String redirectUri,
                                @RequestParam(name = "state", required = false) String state,
                                @RequestParam(name = "userId", required = false) String userId,
                                @RequestParam(name = "applicationCode", required = false) String applicationCode,
                                @RequestParam(name = "denied", required = false) Boolean denied) {

        return modelAndViewService.confirmConsent(
            sessionId,
            userId,
            clientId,
            redirectUri,
            state,
            applicationCode,
            Boolean.TRUE.equals(denied)
        );
    }
}
