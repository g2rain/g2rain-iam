package com.g2rain.iam.client;

import com.g2rain.basis.api.IdpPassportInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis IdP Passport 解析 Feign 客户端。
 */
@FeignClient(name = "g2rain-basis", contextId = "idpPassportClient", path = "/internal/idp_passport")
public interface IdpPassportClient extends IdpPassportInternalApi {
}
