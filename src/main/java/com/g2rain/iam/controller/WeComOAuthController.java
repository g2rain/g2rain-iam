package com.g2rain.iam.controller;

import com.g2rain.iam.wecom.WeComOAuthResult;
import com.g2rain.iam.service.IamSessionCookieService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.WeComOAuthService;
import com.g2rain.iam.utils.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/auth/wecom")
public class WeComOAuthController {
    private final WeComOAuthService oauthService;
    private final IamSessionCookieService sessionCookieService;
    private final ModelAndViewService modelAndViewService;

    @GetMapping("/authorize")
    @Operation(summary = "跳转企业微信扫码登录", hidden = true)
    @ApiResponse(responseCode = "302", description = "重定向至企业微信扫码页")
    public ModelAndView authorize(
        @RequestParam String bindMode,
        @RequestParam String clientId,
        @RequestParam String redirectUri,
        @RequestParam(required = false) String state) {
        try {
            return new ModelAndView(Constants.REDIRECT
                + oauthService.buildAuthorizeUrl(
                    bindMode, clientId, redirectUri, state));
        } catch (Exception exception) {
            log.error("企业微信授权跳转失败 bindMode={}", bindMode, exception);
            return modelAndViewService.redirectLogin(
                clientId, redirectUri, state == null ? "" : state,
                "企业微信授权准备失败，请稍后重试", null);
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "企业微信扫码登录回调", hidden = true)
    public ModelAndView callback(
        HttpServletResponse response,
        @RequestParam(name = "auth_code", required = false) String authCode,
        @RequestParam(name = "code", required = false) String code,
        @RequestParam String state) {
        String resolvedCode =
            authCode == null || authCode.isBlank() ? code : authCode;
        try {
            WeComOAuthResult result = oauthService.finishLogin(resolvedCode, state);
            sessionCookieService.writeSessionCookie(response, result.sessionId());
            return modelAndViewService.redirectConsent(
                result.sessionId(), result.clientId(),
                result.redirectUri(), result.state());
        } catch (Exception exception) {
            log.error("企业微信扫码登录失败 stateLen={}",
                state == null ? 0 : state.length(), exception);
            ModelAndView view = new ModelAndView("error");
            view.addObject("error", "企业微信登录失败，请返回应用重新发起授权");
            view.addObject("redirectUri", "");
            view.addObject("state", state == null ? "" : state);
            return view;
        }
    }
}
