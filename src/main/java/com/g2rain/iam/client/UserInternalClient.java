package com.g2rain.iam.client;

import com.g2rain.basis.api.UserInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis 用户无隔离查询 Feign 客户端。
 */
@FeignClient(name = "g2rain-basis", contextId = "userInternalClient", path = "/internal/user")
public interface UserInternalClient extends UserInternalApi {
}
