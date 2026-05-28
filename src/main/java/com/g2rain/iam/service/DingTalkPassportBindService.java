package com.g2rain.iam.service;

import com.g2rain.basis.dto.PassportIdpBindingBindDto;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.iam.client.PassportIdpBindingClient;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.DingTalkPassportBindStartDto;
import com.g2rain.iam.dto.DingTalkPassportBindStateDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.vo.DingTalkPassportBindStartVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 已登录通行证绑定钉钉服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkPassportBindService {

    private final TokenService tokenService;
    private final DingTalkIamProperties dingTalkIamProperties;
    private final IamAccessProperties iamAccessProperties;
    private final DingTalkPassportBindStateService dingTalkPassportBindStateService;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
    private final PassportIdpBindingClient passportIdpBindingClient;

    /**
     * 启动扫码绑定：校验 access token，写入 state，返回 gotoUrl
     */
    public DingTalkPassportBindStartVo start(String authorization, DingTalkPassportBindStartDto req) {
        TokenJWTPayload token = tokenService.requireValidAccessToken(authorization);
        String bindMode = resolveBindMode(req == null ? null : req.getBindMode());
        String returnUrl = resolveReturnUrl(req == null ? null : req.getReturnUrl());

        String sessionType = token.getSessionType() == null ? null : token.getSessionType().name();
        String gotoUrl = dingTalkPassportBindStateService.persistStateAndBuildQrGotoUrl(
            token.getPassportId(),
            token.getOrganId(),
            bindMode,
            returnUrl,
            sessionType,
            token.isAdminUser()
        );
        return new DingTalkPassportBindStartVo(gotoUrl);
    }

    /**
     * 钉钉回调：换票并落库，返回重定向 URL
     */
    public String finishBindAndBuildRedirectUrl(String authCode, String opaqueState) {
        DingTalkPassportBindStateDto state = dingTalkPassportBindStateService.consumeState(opaqueState);
        try {
            DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(state.getBindMode());
            DingTalkPrincipal principal = adapter.exchangeCodeForPrincipal(authCode, true);

            PassportIdpBindingBindDto bindDto = new PassportIdpBindingBindDto();
            bindDto.setPassportId(state.getPassportId());
            bindDto.setOrganId(state.getOrganId());
            bindDto.setIdpType(IdpType.DINGTALK.name());
            bindDto.setIdpSubject(principal.unionId());
            bindDto.setCorpId(Strings.isBlank(principal.corpId()) ? null : principal.corpId().trim());
            bindDto.setIdpUserId(Strings.isBlank(principal.openId()) ? null : principal.openId().trim());
            bindDto.setIdpApplicationCode(
                principal.idpApplicationCode() == null ? "" : principal.idpApplicationCode().trim()
            );
            bindDto.setBindMode(principal.bindMode());
            bindDto.setRawProfile(Strings.isBlank(principal.rawJson()) ? "{}" : principal.rawJson());
            bindDto.setSessionType(state.getSessionType());
            bindDto.setAdminUser(state.getAdminUser());

            Result<Long> result = passportIdpBindingClient.bind(bindDto);
            if (!result.isSuccess()) {
                throw ExceptionConverter.of(result);
            }
            return buildResultRedirect(state.getReturnUrl(), true, null, null);
        } catch (BusinessException e) {
            log.warn("passport dingtalk bind failed passportId={} code={} message={}",
                state.getPassportId(), e.getErrorCode() == null ? "" : e.getErrorCode(), e.getMessage());
            return buildResultRedirect(
                state.getReturnUrl(),
                false,
                e.getErrorCode() == null ? "BIND_FAILED" : e.getErrorCode(),
                e.getMessage()
            );
        } catch (Exception e) {
            log.error("passport dingtalk bind error passportId={}", state.getPassportId(), e);
            return buildResultRedirect(state.getReturnUrl(), false, "BIND_FAILED", "绑定失败，请稍后重试");
        }
    }

    /**
     * state 失效时的回跳地址
     */
    public String bindErrorRedirectUrl(String opaqueState, String message) {
        String returnUrl = dingTalkPassportBindStateService.peekState(opaqueState)
            .map(DingTalkPassportBindStateDto::getReturnUrl)
            .orElseGet(() -> dingTalkIamProperties.defaultPassportBindResultUrl(
                iamAccessProperties.resolvedPlatformBaseUrl()
            ));
        return buildResultRedirect(returnUrl, false, "INVALID_STATE", message);
    }

    private String resolveBindMode(String requested) {
        if (Strings.isNotBlank(requested)) {
            return requested.trim();
        }
        String configured = dingTalkIamProperties.getLoginPageBindMode();
        if (Strings.isNotBlank(configured)) {
            return configured.trim();
        }
        throw new BusinessException(com.g2rain.iam.enums.IamErrorCode.DINGTALK_PASSPORT_BIND_CONTEXT_INVALID);
    }

    private String resolveReturnUrl(String requested) {
        if (Strings.isNotBlank(requested)) {
            return requested.trim();
        }
        return dingTalkIamProperties.defaultPassportBindResultUrl(iamAccessProperties.resolvedPlatformBaseUrl());
    }

    private static String buildResultRedirect(String returnUrl, boolean success, String code, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(returnUrl)
            .queryParam("success", success ? "1" : "0");
        if (!success && Strings.isNotBlank(code)) {
            builder.queryParam("code", code);
        }
        if (!success && Strings.isNotBlank(message)) {
            builder.queryParam("message", URLEncoder.encode(message, StandardCharsets.UTF_8));
        }
        return builder.build(true).toUriString();
    }
}
