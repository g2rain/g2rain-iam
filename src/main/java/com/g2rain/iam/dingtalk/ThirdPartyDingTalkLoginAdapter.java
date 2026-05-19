package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.iam.config.DingTalkIamProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 钉钉第三方企业应用换票适配器
 * 接入形态: {@link IdpBindMode#THIRD_PARTY}
 *
 * @author Alpha
 */
@Component
public class ThirdPartyDingTalkLoginAdapter extends AbstractDingTalkLoginAdapter {

    public ThirdPartyDingTalkLoginAdapter(DingTalkIamProperties dingTalkIamProperties,
                                          @Qualifier("dingTalkRestClient") RestClient dingTalkRestClient,
                                          ObjectMapper objectMapper) {
        super(dingTalkIamProperties, dingTalkRestClient, objectMapper);
    }

    @Override
    public IdpBindMode bindMode() {
        return IdpBindMode.THIRD_PARTY;
    }

    @Override
    protected String clientId() {
        return dingTalkIamProperties.getThirdParty().getClientId();
    }

    @Override
    protected String clientSecret() {
        return dingTalkIamProperties.getThirdParty().getClientSecret();
    }
}
