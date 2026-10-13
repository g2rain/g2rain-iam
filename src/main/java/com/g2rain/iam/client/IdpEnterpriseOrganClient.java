package com.g2rain.iam.client;

import com.g2rain.basis.api.IdpEnterpriseOrganApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    name = "g2rain-basis",
    contextId = "idpEnterpriseOrganClient"
)
public interface IdpEnterpriseOrganClient extends IdpEnterpriseOrganApi {
}
