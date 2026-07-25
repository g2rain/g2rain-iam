package com.g2rain.iam.wecom;

import com.g2rain.basis.enums.IdpBindMode;
import org.springframework.stereotype.Service;

/**
 * 企业微信登录换票适配器路由服务
 */
@Service
public class WeComLoginAdapterRouter {

    private final InternalWeComLoginAdapter internalWeComLoginAdapter;
    private final ThirdPartyWeComLoginAdapter thirdPartyWeComLoginAdapter;

    public WeComLoginAdapterRouter(
        InternalWeComLoginAdapter internalWeComLoginAdapter,
        ThirdPartyWeComLoginAdapter thirdPartyWeComLoginAdapter
    ) {
        this.internalWeComLoginAdapter = internalWeComLoginAdapter;
        this.thirdPartyWeComLoginAdapter = thirdPartyWeComLoginAdapter;
    }

    /**
     * 解析换票适配器
     *
     * @param bindMode IdP 接入形态，{@link IdpBindMode} 枚举名
     * @return 对应的换票适配器
     */
    public WeComLoginAdapter resolve(String bindMode) {
        IdpBindMode.validate(bindMode);
        if (IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            return thirdPartyWeComLoginAdapter;
        }
        return internalWeComLoginAdapter;
    }
}
