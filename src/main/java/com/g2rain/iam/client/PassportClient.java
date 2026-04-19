package com.g2rain.iam.client;


import com.g2rain.basis.api.PassportApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Passport 相关 Feign 客户端，代理调用 g2rain-basis 的 {@link PassportApi}。
 *
 * <p>底层服务提供的路径为 {@code /passport/**}，本客户端通过 Feign 进行转发。</p>
 */
@FeignClient(name = "g2rain-basis", contextId = "passportClient", path = "/passport")
public interface PassportClient extends PassportApi {
}

