package com.g2rain.iam.client;


import com.g2rain.basis.api.LoginApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author alpha
 * @since 2026/1/16
 */
@FeignClient(name = "g2rain-basis", contextId = "loginClient", path = "/internal_auth")
public interface LoginClient extends LoginApi {
}
