package com.g2rain.iam.controller;


import com.g2rain.common.utils.Strings;
import com.g2rain.iam.service.AuthorizationService;
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
     * 授权服务，处理授权码的生成等业务逻辑。
     */
    private AuthorizationService authorizationService;

    private SessionService sessionService;
    /**
     * 服务用于处理 {@link ModelAndView} 对象的创建和管理。
     * 该服务利用所提供的参数（如客户端 ID、重定向 URI 和状态）来生成适当的视图和重定向响应。
     * 它支持诸如错误重定向和登录页面重定向等操作，
     * 这些操作对于授权过程中的用户流程管理至关重要。
     */
    private ModelAndViewService modelAndViewService;

    /**
     * 授权码请求页面，用户发起授权请求时的处理方法。
     * <p>
     * 检查用户是否已登录，如果没有登录则重定向到登录页面；如果会话已过期，则重新登录；如果已登录，则跳转到确认授权页面。
     * </p>
     *
     * @param sessionId   当前用户会话 ID
     * @param clientId    客户端 ID
     * @param redirectUri 授权后重定向的 URI
     * @param state       请求的状态参数，通常用于防止 CSRF 攻击
     * @return {@link ModelAndView}，包含跳转到登录页或授权确认页的视图
     */
    @GetMapping(value = "/authorize")
    public ModelAndView authorize(@CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
                                  @RequestParam(name = "clientId", required = false) String clientId,
                                  @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                  @RequestParam(name = "state", required = false) String state) {

        // 检查 clientId 和 redirectUri 是否为空，若为空则返回错误页面
        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            return modelAndViewService.redirectError(clientId, redirectUri, state);
        }

        if (AuthorizationState.isAnonymous(state)) {
            return modelAndViewService.redirectAnonymousCallback(clientId, redirectUri, state);
        }

        // 检查 sessionId 是否为空，若为空说明未登录，跳转到登录页
        if (Strings.isBlank(sessionId)) {
            return modelAndViewService.redirectLogin(clientId, redirectUri, state);
        }

        // 检查会话是否过期，若会话已过期，跳转到登录页
        if (sessionService.isSessionExpired(sessionId)) {
            return modelAndViewService.redirectLogin(clientId, redirectUri, state);
        }

        // 已登录但未确认授权，跳转到授权确认页
        return modelAndViewService.redirectConsent(sessionId, clientId, redirectUri, state);
    }

    /**
     * 授权码确认页面，用户确认授权后生成授权码并重定向到客户端的回调地址。
     * <p>
     * 检查用户的登录状态，发放授权码并跳转回客户端的指定 URI。
     * </p>
     *
     * @param sessionId   当前用户会话 ID
     * @param clientId    客户端 ID
     * @param redirectUri 授权后重定向的 URI
     * @param state       请求的状态参数
     * @param userId      用户 ID
     * @return {@link ModelAndView}，包含跳转到客户端回调 URI 的视图
     */
    @PostMapping(value = "/authorize_selected")
    public ModelAndView consent(@CookieValue(name = Constants.SESSION_NAME, required = false) String sessionId,
                                @RequestParam(name = "clientId") String clientId,
                                @RequestParam(name = "redirectUri") String redirectUri,
                                @RequestParam(name = "state", required = false) String state,
                                @RequestParam(name = "userId", required = false) String userId) {

        return modelAndViewService.redirectCallback(sessionId, userId, clientId, redirectUri, state);
    }
}
