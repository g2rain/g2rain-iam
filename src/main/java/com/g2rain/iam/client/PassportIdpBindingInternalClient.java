package com.g2rain.iam.client;

import com.g2rain.basis.api.PassportIdpBindingInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis passport_idp_binding 内部 Feign 客户端（IAM 扫码绑定）。
 */
@FeignClient(name = "g2rain-basis", contextId = "passportIdpBindingInternalClient", path = "/internal/passport_idp_binding")
public interface PassportIdpBindingInternalClient extends PassportIdpBindingInternalApi {
}
