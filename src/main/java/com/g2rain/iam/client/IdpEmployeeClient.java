package com.g2rain.iam.client;

import com.g2rain.basis.api.IdpEmployeeApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    name = "g2rain-basis",
    contextId = "idpEmployeeClient"
)
public interface IdpEmployeeClient extends IdpEmployeeApi {
}
