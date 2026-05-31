package com.g2rain.iam.client;

import com.g2rain.basis.api.PassportIdpBindingApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis 账号与 IdP 绑定 Feign 客户端，代理调用 {@link PassportIdpBindingApi}。
 */
@FeignClient(name = "g2rain-basis", contextId = "passportIdpBindingClient", path = "/passport_idp_binding")
public interface PassportIdpBindingClient extends PassportIdpBindingApi {
}
