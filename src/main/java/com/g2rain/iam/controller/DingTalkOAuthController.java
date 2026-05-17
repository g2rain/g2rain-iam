package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.DingTalkQrBootstrapDto;
import com.g2rain.iam.dto.DingTalkStreamAuthorizationDto;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.service.DingTalkOAuthService;
import com.g2rain.iam.service.DingTalkQrBootstrapService;
import com.g2rain.iam.service.DingTalkStreamAuthorizationService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import com.g2rain.iam.vo.DingTalkStreamAuthorizationVo;
import jakarta.servlet.http.Cookie;
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

/**
 * 钉钉 OAuth：浏览器跳转授权/回调；以及 Stream / 消息应用侧基于 unionId 的 JSON 发码。
 * <p>浏览器链路与现有密码登录共用 {@link Constants#SESSION_NAME} Cookie；换票成功后走 {@link ModelAndViewService#redirectConsent}（与 {@link LoginController} 一致）。</p>
 * <p>内嵌扫码（方式二）：{@link #qrBootstrap} 返回 {@code oapi.../sns_authorize} 的 {@code goto}，前端 {@code DDLogin} 扫码后拼接 {@code loginTmpCode} 再跳转，仍回落到 {@code /callback}。</p>
 */
@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping(value = "/auth/dingtalk")
public class DingTalkOAuthController {

    private final DingTalkQrBootstrapService dingTalkQrBootstrapService;
    private final DingTalkOAuthService dingTalkOAuthService;
    private final DingTalkStreamAuthorizationService dingTalkStreamAuthorizationService;
    private final ModelAndViewService modelAndViewService;

    /**
     * 登录页内嵌扫码（方式二）：申请 {@code sns_authorize} 的 {@code goto} URL（写入 Redis state，与 {@link #authorize} 共用回调换票）。
     */
    @PostMapping("/qr/bootstrap")
    @ResponseBody
    public Result<DingTalkQrBootstrapVo> qrBootstrap(@Valid @RequestBody DingTalkQrBootstrapDto dto) {
        return Result.success(dingTalkQrBootstrapService.buildQrBootstrap(
            dto.getBindMode(),
            dto.getClientId(),
            dto.getRedirectUri(),
            dto.getState()
        ));
    }

    /**
     * 跳转钉钉授权页。{@code bindMode} 为 {@link com.g2rain.basis.enums.IdpBindMode} 枚举名；
     * {@code clientId}、{@code redirectUri}、{@code state} 与 {@code /auth/authorize} 参数一致。
     */
    @GetMapping("/authorize")
    public ModelAndView authorize(@RequestParam(name = "bindMode") String bindMode,
                                  @RequestParam(name = "clientId") String clientId,
                                  @RequestParam(name = "redirectUri") String redirectUri,
                                  @RequestParam(name = "state", required = false) String state) {
        try {
            String url = dingTalkOAuthService.buildDingTalkAuthorizeRedirectUrl(bindMode, clientId, redirectUri, state);
            return new ModelAndView(Constants.REDIRECT + url);
        } catch (Exception e) {
            log.error("钉钉授权跳转失败 bindMode={} message={}", bindMode, e.getMessage(), e);
            return modelAndViewService.redirectLogin(
                clientId,
                redirectUri,
                state != null ? state : "",
                "钉钉授权准备失败，请稍后重试或改用账号密码登录",
                null
            );
        }
    }

    /**
     * 钉钉授权回调：换票、写会话；与 {@link LoginController#login} 一致，经 {@link ModelAndViewService#redirectConsent} 进入同意页或直发授权码。
     */
    @GetMapping("/callback")
    public ModelAndView callback(HttpServletResponse response,
                                 @RequestParam(name = "code") String code,
                                 @RequestParam(name = "state") String state) {
        try {
            DingTalkOAuthResult result = dingTalkOAuthService.finishLogin(code, state);
            Cookie cookie = new Cookie(Constants.SESSION_NAME, result.sessionId());
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(24 * 60 * 60);
            response.addCookie(cookie);
            return modelAndViewService.redirectConsent(
                result.sessionId(), result.clientId(), result.redirectUri(), result.state());
        } catch (Exception e) {
            log.error(
                "钉钉登录回调处理失败 codeLen={} stateLen={} exType={} message={}",
                code == null ? 0 : code.length(),
                state == null ? 0 : state.length(),
                e.getClass().getName(),
                e.getMessage(),
                e
            );
            ModelAndView mv = new ModelAndView("error");
            mv.addObject("error", "钉钉登录失败，请返回应用重新发起授权");
            mv.addObject("redirectUri", "");
            mv.addObject("state", state != null ? state : "");
            return mv;
        }
    }

    /**
     * 发放 OAuth 授权码（JSON，供 Stream / 消息应用换 token）。
     */
    @PostMapping("/authorize_code")
    @ResponseBody
    public Result<DingTalkStreamAuthorizationVo> authorizeCode(
        @Valid @RequestBody DingTalkStreamAuthorizationDto dto
    ) {
        return Result.success(dingTalkStreamAuthorizationService.issueStreamAuthorizationCode(dto));
    }
}
