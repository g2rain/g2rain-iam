package com.g2rain.iam.client;

import com.g2rain.basis.api.UserApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis user Feign 客户端。
 */
@FeignClient(name = "g2rain-basis", contextId = "userClient", path = "/user")
public interface UserClient extends UserApi {
}
