package com.g2rain.iam.client;

import com.g2rain.basis.dto.PassportIdpBindingDto;
import com.g2rain.basis.dto.PassportIdpBindingSelectDto;
import com.g2rain.basis.vo.PassportIdpBindingVo;
import com.g2rain.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Basis 账号与 IdP 绑定 Feign 客户端（与 {@code PassportIdpBindingApi} 路径一致；GET 列表使用 {@link SpringQueryMap}）。
 */
@FeignClient(name = "g2rain-basis", contextId = "passportIdpBindingClient", path = "/passport_idp_binding")
public interface PassportIdpBindingClient {

    @GetMapping("/list")
    Result<List<PassportIdpBindingVo>> selectList(@SpringQueryMap PassportIdpBindingSelectDto query);

    @PostMapping("/save")
    Result<Long> save(@RequestBody PassportIdpBindingDto dto);
}
