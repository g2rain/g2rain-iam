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

    /**
     * 租户开通权限校验服务。
     */
    @Resource
    private TenantProvisionPermissionService tenantProvisionPermissionService;

    /**
     * 校验指定 Passport 是否具备创建机构（开户）资格。
     * <p>
     * 以 IdP 管理员登录时写入的 Redis 资格标记为准；无浏览器 Cookie。
     * </p>
     *
     * @param request 含 passportId 的校验请求
     * @return 空成功结果；无资格时抛业务异常
     */
    @PostMapping("/verify_create_organ")
    public Result<Void> verifyCreateOrgan(@RequestBody @Validated VerifyCreateOrganRequest request) {
        tenantProvisionPermissionService.verifyCanCreateOrgan(request.getPassportId());
        return Result.success(null);
    }
}