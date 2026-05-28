package com.g2rain.iam.service;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.DingTalkPassportBindStateDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 通行证绑定钉钉扫码 state 服务
 */
@Service
@RequiredArgsConstructor
public class DingTalkPassportBindStateService {

    private final IamAccessProperties iamAccessProperties;
    private final DingTalkIamProperties dingTalkIamProperties;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;

    /**
     * 持久化绑定上下文并生成内嵌扫码 goto URL
     */
    public String persistStateAndBuildQrGotoUrl(
        Long passportId,
        Long organId,
        String bindMode,
        String returnUrl,
        String sessionType,
        Boolean adminUser
    ) {
        IdpBindMode.validate(bindMode);
        if (passportId == null || passportId <= 0L || organId == null || organId <= 0L) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_CONTEXT_INVALID);
        }
        if (Strings.isBlank(returnUrl)) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_CONTEXT_INVALID);
        }

        String opaqueState = IamUtils.generateAuthorizationCode();
        DingTalkPassportBindStateDto payload = new DingTalkPassportBindStateDto();
        payload.setPassportId(passportId);
        payload.setOrganId(organId);
        payload.setBindMode(bindMode.trim());
        payload.setReturnUrl(returnUrl.trim());
        payload.setSessionType(Strings.isBlank(sessionType) ? null : sessionType.trim());
        payload.setAdminUser(adminUser);

        genericRedisHelper.set(
            RedisKeyRule.DINGTALK_PASSPORT_BIND_STATE.format(opaqueState),
            payload,
            Duration.ofMinutes(10)
        );

        String callback = dingTalkIamProperties.fullPassportBindCallbackUrl(
            iamAccessProperties.resolvedPlatformBaseUrl()
        );
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(bindMode);
        return adapter.buildQrEmbeddedAuthorizeUrl(opaqueState, callback);
    }

    /**
     * 读取并一次性消费 state
     */
    public DingTalkPassportBindStateDto consumeState(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_INVALID_STATE);
        }
        String key = RedisKeyRule.DINGTALK_PASSPORT_BIND_STATE.format(opaqueState.trim());
        DingTalkPassportBindStateDto payload = genericRedisHelper.get(key, DingTalkPassportBindStateDto.class);
        if (payload == null) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_INVALID_STATE);
        }
        genericRedisHelper.delete(key);
        if (payload.getPassportId() == null || payload.getOrganId() == null
            || Strings.isBlank(payload.getBindMode()) || Strings.isBlank(payload.getReturnUrl())) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_INVALID_STATE);
        }
        return payload;
    }

    /**
     * 仅读取 state（失败页回跳用，不删除）
     */
    public Optional<DingTalkPassportBindStateDto> peekState(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            return Optional.empty();
        }
        return Optional.ofNullable(genericRedisHelper.get(
            RedisKeyRule.DINGTALK_PASSPORT_BIND_STATE.format(opaqueState.trim()),
            DingTalkPassportBindStateDto.class
        ));
    }
}
