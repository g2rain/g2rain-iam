package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.VerifyCreateOrganRequest;
import com.g2rain.iam.service.TenantProvisionPermissionService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户开通内部接口，供 g2rain-basis 通过 Feign 调用。
 */
@RestController
@RequestMapping("/internal/tenant_provision")
public class TenantProvisionInternalController {

    @Resource
    private TenantProvisionPermissionService tenantProvisionPermissionService;

    @PostMapping("/verify_create_organ")
    public Result<Void> verifyCreateOrgan(@RequestBody @Validated VerifyCreateOrganRequest request) {
        tenantProvisionPermissionService.verifyCanCreateOrgan(request.getPassportId());
        return Result.success(null);
    }
}