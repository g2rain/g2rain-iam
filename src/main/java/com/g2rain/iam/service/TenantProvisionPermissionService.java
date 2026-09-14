package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户开通（创建 organ）权限校验：仅钉钉/企微企业管理员会话允许。
 * <p>Basis 经 Feign 调用本校验时无浏览器 Cookie，因此以 ADMIN 登录时写入的 Redis 资格标记为准。</p>
 */
@Service
@RequiredArgsConstructor
public class TenantProvisionPermissionService {

    private final GenericRedisHelper genericRedisHelper;

    public void verifyCanCreateOrgan(Long passportId) {
        if (passportId == null || passportId <= 0) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "passportId");
        }
        String key = RedisKeyRule.IDP_TENANT_PROVISION_ELIGIBLE.format(String.valueOf(passportId));
        String eligible = genericRedisHelper.get(key, String.class);
        if (Strings.isBlank(eligible)) {
            throw new BusinessException(IamErrorCode.TENANT_PROVISION_ADMIN_REQUIRED);
        }
    }
}
