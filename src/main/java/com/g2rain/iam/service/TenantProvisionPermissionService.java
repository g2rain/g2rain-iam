package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import org.springframework.stereotype.Service;

/**
 * 租户开通（创建 organ）权限校验。
 * <p>
 * 本阶段全部允许；后续可在同方法内按 IdP 通道扩展拒绝逻辑。
 * </p>
 */
@Service
public class TenantProvisionPermissionService {

    public void verifyCanCreateOrgan(Long passportId) {
        if (passportId == null || passportId <= 0) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "passportId");
        }
    }
}
