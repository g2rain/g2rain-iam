package com.g2rain.iam.client;

import com.g2rain.basis.api.IdpEnterpriseApplicationAuthorizationApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    name = "g2rain-basis",
    contextId = "idpEnterpriseApplicationAuthorizationClient"
)
public interface IdpEnterpriseApplicationAuthorizationClient
    extends IdpEnterpriseApplicationAuthorizationApi {
}
