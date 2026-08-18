package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantProvisionPermissionServiceTest {

    private final TenantProvisionPermissionService service = new TenantProvisionPermissionService();

    @Test
    void verifyCanCreateOrgan_shouldAllowValidPassportId() {
        assertDoesNotThrow(() -> service.verifyCanCreateOrgan(10001L));
    }

    @Test
    void verifyCanCreateOrgan_shouldRejectNullPassportId() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.verifyCanCreateOrgan(null));
        assertEquals(SystemErrorCode.PARAM_VAL_INVALID.code(), ex.getErrorCode());
    }

    @Test
    void verifyCanCreateOrgan_shouldRejectNonPositivePassportId() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.verifyCanCreateOrgan(0L));
        assertEquals(SystemErrorCode.PARAM_VAL_INVALID.code(), ex.getErrorCode());
    }
}
