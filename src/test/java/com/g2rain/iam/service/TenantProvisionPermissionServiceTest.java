package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisionPermissionServiceTest {

    @Mock
    private GenericRedisHelper genericRedisHelper;

    private TenantProvisionPermissionService service;

    @BeforeEach
    void setUp() {
        service = new TenantProvisionPermissionService(genericRedisHelper);
    }

    @Test
    void verifyAllowsEligibleAdmin() {
        when(genericRedisHelper.get(
            RedisKeyRule.IDP_TENANT_PROVISION_ELIGIBLE.format("100"),
            String.class
        )).thenReturn("1");
        service.verifyCanCreateOrgan(100L);
    }

    @Test
    void verifyRejectsMissingEligibility() {
        when(genericRedisHelper.get(
            RedisKeyRule.IDP_TENANT_PROVISION_ELIGIBLE.format("100"),
            String.class
        )).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.verifyCanCreateOrgan(100L));
        assertEquals(IamErrorCode.TENANT_PROVISION_ADMIN_REQUIRED.code(), ex.getErrorCode());
    }

    @Test
    void verifyRejectsNullPassportId() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.verifyCanCreateOrgan(null));
        assertEquals(com.g2rain.common.exception.SystemErrorCode.PARAM_VAL_INVALID.code(), ex.getErrorCode());
    }
}
