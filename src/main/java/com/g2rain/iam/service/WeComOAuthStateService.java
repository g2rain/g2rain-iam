package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.WeComOAuthStateDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import com.g2rain.iam.wecom.WeComLoginAdapter;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WeComOAuthStateService {

    private final IamAccessProperties iamAccessProperties;
    private final WeComIamProperties properties;
    private final GenericRedisHelper redis;
    private final WeComLoginAdapterRouter weComLoginAdapterRouter;

    public String persistAndBuildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state) {
        if (Strings.isBlank(clientId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "clientId");
        }
        if (Strings.isBlank(redirectUri)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "redirectUri");
        }
        WeComLoginAdapter adapter = weComLoginAdapterRouter.resolve(bindMode);
        String opaqueState = IamUtils.generateAuthorizationCode();
        WeComOAuthStateDto payload = new WeComOAuthStateDto();
        payload.setBindMode(adapter.bindMode().name());
        payload.setClientId(clientId.trim());
        payload.setRedirectUri(redirectUri.trim());
        payload.setState(state);
        redis.set(RedisKeyRule.WECOM_OAUTH_STATE.format(opaqueState),
            payload, Duration.ofMinutes(10));
        String callback =
            properties.fullCallbackUrl(iamAccessProperties.normalizedBaseUrl());
        return adapter.buildAuthorizeUrl(opaqueState, callback);
    }

    public WeComOAuthStateDto consume(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            throw new BusinessException(IamErrorCode.WECOM_OAUTH_INVALID_STATE);
        }
        String key = RedisKeyRule.WECOM_OAUTH_STATE.format(opaqueState.trim());
        WeComOAuthStateDto payload = redis.get(key, WeComOAuthStateDto.class);
        if (payload == null) {
            throw new BusinessException(IamErrorCode.WECOM_OAUTH_INVALID_STATE);
        }
        redis.delete(key);
        return payload;
    }
}
