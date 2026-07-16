package com.g2rain.iam.client;

import com.g2rain.basis.api.PassportIdpBindingApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis passport_idp_binding Feign 客户端。
 */
@FeignClient(name = "g2rain-basis", contextId = "passportIdpBindingClient", path = "/passport_idp_binding")
public interface PassportIdpBindingClient extends PassportIdpBindingApi {
}
