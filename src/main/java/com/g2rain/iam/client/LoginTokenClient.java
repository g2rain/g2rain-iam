package com.g2rain.iam.client;


import com.g2rain.basis.api.LoginTokenApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/1/16
 */
@FeignClient(name = "g2rain-basis", contextId = "loginTokenClient", path = "/login_token")
public interface LoginTokenClient extends LoginTokenApi {
}
