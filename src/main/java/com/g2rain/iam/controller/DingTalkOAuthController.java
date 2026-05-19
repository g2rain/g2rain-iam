package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dto.DingTalkQrBootstrapDto;
import com.g2rain.iam.dto.DingTalkStreamAuthorizationDto;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.service.DingTalkOAuthService;
import com.g2rain.iam.service.DingTalkQrBootstrapService;
import com.g2rain.iam.service.DingTalkStreamAuthorizationService;
import com.g2rain.iam.service.IamSessionCookieService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import com.g2rain.iam.vo.DingTalkStreamAuthorizationVo;
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
 * 钉钉 OAuth 控制器
 * 路径前缀: /auth/dingtalk
 *
 * @author Alpha
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

    private final IamSessionCookieService iamSessionCookieService;

    /**
     * 申请内嵌扫码（方式二）的 sns 授权 goto URL
     *
     * @param dto 内嵌扫码引导请求 DTO（已校验）
     * @return 包含 goto URL 的视图对象
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
     * 跳转钉钉授权页（方式一浏览器整页 OAuth）
     *
     * @param bindMode    IdP 接入形态，{@link com.g2rain.basis.enums.IdpBindMode} 枚举名
     * @param clientId    OAuth2 客户端 ID
     * @param redirectUri OAuth2 回调地址
     * @param state       业务系统 state，可选
     * @return 重定向至钉钉授权页的视图
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
     * 钉钉授权回调：换票、写会话 Cookie，并进入 OAuth 同意页或直发授权码
     *
     * @param response    HTTP 响应，用于写入会话 Cookie
     * @param code        钉钉授权码
     * @param opaqueState IAM 下发的 opaque state（钉钉回调参数名仍为 state）
     * @return 同意页重定向或错误页视图
     */
    @GetMapping("/callback")
    public ModelAndView callback(HttpServletResponse response,
                                 @RequestParam(name = "code") String code,
                                 @RequestParam(name = "state") String opaqueState) {
        try {
            DingTalkOAuthResult result = dingTalkOAuthService.finishLogin(code, opaqueState);
            iamSessionCookieService.writeSessionCookie(response, result.sessionId());
            return modelAndViewService.redirectConsent(
                result.sessionId(), result.clientId(), result.redirectUri(), result.state());
        } catch (Exception e) {
            log.error(
                "钉钉登录回调处理失败 codeLen={} stateLen={} exType={} message={}",
                code == null ? 0 : code.length(),
                opaqueState == null ? 0 : opaqueState.length(),
                e.getClass().getName(),
                e.getMessage(),
                e
            );
            return dingTalkCallbackErrorView(opaqueState, "钉钉登录失败，请返回应用重新发起授权");
        }
    }

    /**
     * 回调失败时根据 opaque state 恢复 OAuth 上下文并展示错误页或返回登录页
     *
     * @param opaqueState   IAM opaque state
     * @param errorMessage  展示给用户的错误提示
     * @return 登录页重定向或错误页视图
     */
    private ModelAndView dingTalkCallbackErrorView(String opaqueState, String errorMessage) {
        Optional<DingTalkOAuthStateDto> payloadOpt = dingTalkOAuthService.peekOAuthState(opaqueState);
        if (payloadOpt.isPresent()) {
            DingTalkOAuthStateDto payload = payloadOpt.get();
            String clientId = payload.getClientId() == null ? "" : payload.getClientId().trim();
            String redirectUri = payload.getRedirectUri() == null ? "" : payload.getRedirectUri().trim();
            String oauthState = payload.getState() == null ? "" : payload.getState();
            if (Strings.isNotBlank(clientId) && Strings.isNotBlank(redirectUri)) {
                return modelAndViewService.redirectLogin(clientId, redirectUri, oauthState, errorMessage, null);
            }
            ModelAndView mv = new ModelAndView("error");
            mv.addObject("error", errorMessage);
            mv.addObject("redirectUri", redirectUri);
            mv.addObject("state", oauthState);
            return mv;
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("error", errorMessage);
        mv.addObject("redirectUri", "");
        mv.addObject("state", opaqueState != null ? opaqueState : "");
        return mv;
    }

    /**
     * 发放 OAuth 授权码（JSON，供 Stream / 消息应用换 token）
     *
     * @param dto Stream 发码请求 DTO（已校验）
     * @return 授权码及 state 的视图对象
     */
    @PostMapping("/authorize_code")
    @ResponseBody
    public Result<DingTalkStreamAuthorizationVo> authorizeCode(
        @Valid @RequestBody DingTalkStreamAuthorizationDto dto
    ) {
        return Result.success(dingTalkStreamAuthorizationService.issueStreamAuthorizationCode(dto));
    }
}
