package com.g2rain.iam.service.idp;

import com.g2rain.basis.dto.IdpEmployeeEnsureRequest;
import com.g2rain.basis.vo.IdpEmployeeEnsureVo;
import com.g2rain.basis.vo.IdpEnterpriseOrganResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.IdpEmployeeClient;
import com.g2rain.iam.client.IdpEnterpriseOrganClient;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.idp.IdpPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdpEmployeeLoginOrchestratorTest {

    @Mock
    private IdpAuthServiceRouter idpAuthServiceRouter;
    @Mock
    private IdpEnterpriseOrganClient organClient;
    @Mock
    private IdpEmployeeClient employeeClient;

    private IdpEmployeeLoginOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new IdpEmployeeLoginOrchestrator(idpAuthServiceRouter, organClient, employeeClient);
    }

    @Test
    void userWithoutOrganIsRejected() {
        IdpPrincipal principal = principal();
        when(idpAuthServiceRouter.resolvePassportId(principal, true)).thenReturn("11");
        when(organClient.resolve(any())).thenReturn(Result.error("basis.40068", "not found"));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> orchestrator.resolvePassportForEmployeeLogin(principal, IdpLoginRole.USER, true));
        assertEquals(IamErrorCode.IDP_ENTERPRISE_ORGAN_NOT_READY.code(), ex.getErrorCode());
        verify(employeeClient, never()).ensure(any());
    }

    @Test
    void userWithOrganEnsuresEmployee() {
        IdpPrincipal principal = principal();
        when(idpAuthServiceRouter.resolvePassportId(principal, true)).thenReturn("11");
        IdpEnterpriseOrganResolveVo organ = new IdpEnterpriseOrganResolveVo();
        organ.setOrganId(88L);
        when(organClient.resolve(any())).thenReturn(Result.success(organ));
        IdpEmployeeEnsureVo ensureVo = new IdpEmployeeEnsureVo();
        ensureVo.setUserId(9L);
        when(employeeClient.ensure(any())).thenReturn(Result.success(ensureVo));

        assertEquals("11", orchestrator.resolvePassportForEmployeeLogin(principal, IdpLoginRole.USER, true));

        ArgumentCaptor<IdpEmployeeEnsureRequest> captor = ArgumentCaptor.forClass(IdpEmployeeEnsureRequest.class);
        verify(employeeClient).ensure(captor.capture());
        assertEquals(88L, captor.getValue().getOrganId());
        assertEquals(11L, captor.getValue().getPassportId());
    }

    @Test
    void adminWithoutOrganSkipsEnsure() {
        IdpPrincipal principal = principal();
        when(idpAuthServiceRouter.resolvePassportId(eq(principal), eq(true))).thenReturn("11");
        when(organClient.resolve(any())).thenReturn(Result.error("basis.40068", "not found"));

        assertEquals("11", orchestrator.resolvePassportForEmployeeLogin(principal, IdpLoginRole.ADMIN, true));
        verify(employeeClient, never()).ensure(any());
    }

    private static IdpPrincipal principal() {
        return new IdpPrincipal(
            "DINGTALK",
            "sub-1",
            "uid-1",
            null,
            "corp-1",
            "Alice",
            "INTERNAL",
            null,
            "app"
        );
    }
}
