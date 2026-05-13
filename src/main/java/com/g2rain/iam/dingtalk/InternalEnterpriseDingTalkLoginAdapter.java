package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.iam.config.DingTalkIamProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 钉钉企业内部应用换票链路（{@link IdpBindMode#INTERNAL}）。
 */
@Component
public class InternalEnterpriseDingTalkLoginAdapter extends AbstractDingTalkLoginAdapter {

    public InternalEnterpriseDingTalkLoginAdapter(DingTalkIamProperties dingTalkIamProperties,
                                                  @Qualifier("dingTalkRestClient") RestClient dingTalkRestClient,
                                                  ObjectMapper objectMapper) {
        super(dingTalkIamProperties, dingTalkRestClient, objectMapper);
    }

    @Override
    public IdpBindMode bindMode() {
        return IdpBindMode.INTERNAL;
    }

    @Override
    protected String clientId() {
        return dingTalkIamProperties.getInternal().getClientId();
    }

    @Override
    protected String clientSecret() {
        return dingTalkIamProperties.getInternal().getClientSecret();
    }
}
