package com.g2rain.iam.client;

import com.g2rain.basis.api.ApplicationAuthorizationApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 应用授权 Feign 客户端，对接 g2rain-basis {@code /application_authorization}。
 * <p>IAM consent 确认开通使用 hidden 接口 {@code activate_self}；预览由 IAM 本地拼装。</p>
 */
@FeignClient(name = "g2rain-basis", contextId = "applicationAuthorizationClient", path = "/application_authorization")
public interface ApplicationAuthorizationClient extends ApplicationAuthorizationApi {
}
