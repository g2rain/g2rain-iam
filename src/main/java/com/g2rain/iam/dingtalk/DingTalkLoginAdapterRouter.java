package com.g2rain.iam.dingtalk;

import com.g2rain.basis.enums.IdpBindMode;
import org.springframework.stereotype.Service;

/**
 * 按 {@link IdpBindMode} 选择钉钉换票适配器。
 */
@Service
public class DingTalkLoginAdapterRouter {

    private final InternalEnterpriseDingTalkLoginAdapter internalEnterpriseDingTalkLoginAdapter;
    private final ThirdPartyDingTalkLoginAdapter thirdPartyDingTalkLoginAdapter;

    public DingTalkLoginAdapterRouter(InternalEnterpriseDingTalkLoginAdapter internalEnterpriseDingTalkLoginAdapter,
                                      ThirdPartyDingTalkLoginAdapter thirdPartyDingTalkLoginAdapter) {
        this.internalEnterpriseDingTalkLoginAdapter = internalEnterpriseDingTalkLoginAdapter;
        this.thirdPartyDingTalkLoginAdapter = thirdPartyDingTalkLoginAdapter;
    }

    /**
     * @param bindModeName {@link IdpBindMode} 枚举名
     */
    public DingTalkLoginAdapter resolve(String bindModeName) {
        IdpBindMode.validate(bindModeName);
        if (IdpBindMode.THIRD_PARTY.name().equals(bindModeName)) {
            return thirdPartyDingTalkLoginAdapter;
        }
        return internalEnterpriseDingTalkLoginAdapter;
    }
}
