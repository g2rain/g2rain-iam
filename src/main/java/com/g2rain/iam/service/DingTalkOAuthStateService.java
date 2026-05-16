package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 钉钉 OAuth 前置：将 opaque state 写入 Redis，并生成钉钉授权页 URL；
 * 浏览器跳转走方式一 {@code login.dingtalk.com/oauth2/auth}，内嵌扫码走方式二 {@code oapi.../sns_authorize}。
 */
@Service
@RequiredArgsConstructor
public class DingTalkOAuthStateService {

    private final DingTalkIamProperties dingTalkIamProperties;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;

    /**
     * @param qrEmbedded {@code true} 时返回方式二内嵌扫码 {@code DDLogin} 的 {@code goto}（{@code sns_authorize}）
     */
    public String persistStateAndBuildAuthorizeUrl(String bindMode, String clientId, String clientRedirectUri,
                                                   String clientState, boolean qrEmbedded) {
        if (Strings.isBlank(clientId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "clientId");
        }
        if (Strings.isBlank(clientRedirectUri)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "redirectUri");
        }
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(bindMode);
        String opaque = IamUtils.generateAuthorizationCode();
        DingTalkOAuthStateDto payload = new DingTalkOAuthStateDto();
        payload.setBindMode(bindMode);
        payload.setClientId(clientId);
        payload.setClientRedirectUri(clientRedirectUri);
        payload.setClientState(clientState);
        genericRedisHelper.set(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaque),
            payload,
            Duration.ofMinutes(10)
        );
        String callback = dingTalkIamProperties.fullCallbackUrl();
        if (qrEmbedded) {
            return adapter.buildQrEmbeddedAuthorizeUrl(opaque, callback);
        }
        return adapter.buildAuthorizeUrl(opaque, callback);
    }
}
