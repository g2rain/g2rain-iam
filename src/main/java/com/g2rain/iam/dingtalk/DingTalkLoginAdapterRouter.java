package com.g2rain.iam.dingtalk;

import com.g2rain.basis.enums.IdpBindMode;
import org.springframework.stereotype.Service;

/**
 * 钉钉登录换票适配器路由服务
 * 功能：按 {@link IdpBindMode} 选择具体换票适配器实现
 *
 * @author Alpha
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
     * 解析换票适配器
     *
     * @param bindMode IdP 接入形态，{@link IdpBindMode} 枚举名
     * @return 对应的换票适配器
     */
    public DingTalkLoginAdapter resolve(String bindMode) {
        IdpBindMode.validate(bindMode);
        if (IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            return thirdPartyDingTalkLoginAdapter;
        }
        return internalEnterpriseDingTalkLoginAdapter;
    }
}
