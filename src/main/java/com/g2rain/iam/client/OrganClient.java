package com.g2rain.iam.client;

import com.g2rain.basis.api.OrganApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Basis 机构 Feign 客户端（consent 预览查机构名称等）。
 */
@FeignClient(name = "g2rain-basis", contextId = "organClient", path = "/organ")
public interface OrganClient extends OrganApi {
}
