package com.g2rain.iam.service.idp;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeComIdpAuthServiceTest {

    private IdpBindingSupport idpBindingSupport;
    private IdpPassportProvisioner idpPassportProvisioner;
    private WeComIdpAuthService service;

    private static IdpPrincipal weComPrincipal(String subject) {
        return new IdpPrincipal(
            "WECHAT_WORK",
            subject,
            "user-1",
            "",
            "corp-1",
            "张三",
            "INTERNAL",
            "{}",
            "agent-1"
        );
    }

    @BeforeEach
    void setUp() {
        idpBindingSupport = mock(IdpBindingSupport.class);
        idpPassportProvisioner = mock(IdpPassportProvisioner.class);
        service = new WeComIdpAuthService();
        ReflectionTestUtils.setField(service, "idpBindingSupport", idpBindingSupport);
        ReflectionTestUtils.setField(service, "idpPassportProvisioner", idpPassportProvisioner);
    }

    @Test
    void supportsWeChatWork() {
        assertTrue(service.supports("WECHAT_WORK"));
    }

    @Test
    void returnsExistingBinding() {
        IdpPrincipal principal = weComPrincipal("subject-1");
        when(idpBindingSupport.lookupBinding(principal)).thenReturn(Optional.of("42"));

        assertEquals("42", service.resolvePassportId(principal, true));

        verify(idpPassportProvisioner, never()).registerPassport(any(), any(), any());
    }

    @Test
    void autoProvisionCreatesPassportWithWwPrefix() {
        IdpPrincipal principal = weComPrincipal("subject-1");
        when(idpBindingSupport.lookupBinding(principal)).thenReturn(Optional.empty());
        when(idpPassportProvisioner.registerPassport(eq("ww_subject-1"), eq("张三"), eq("企业微信用户")))
            .thenAnswer(invocation -> Result.success(100L));

        assertEquals("100", service.resolvePassportId(principal, true));

        verify(idpPassportProvisioner).registerPassport("ww_subject-1", "张三", "企业微信用户");
        verify(idpBindingSupport).saveBinding(100L, principal);
    }

    @Test
    void rejectsStreamWhenNotBound() {
        IdpPrincipal principal = weComPrincipal("subject-1");
        when(idpBindingSupport.lookupBinding(principal)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.resolvePassportId(principal, false));

        assertEquals(IamErrorCode.WECOM_STREAM_USER_NOT_BOUND.code(), ex.getErrorCode());
        verify(idpPassportProvisioner, never()).registerPassport(any(), any(), any());
    }

    @Test
    void retriesLookupWhenRegisterFailsDueToRace() {
        IdpPrincipal principal = weComPrincipal("subject-1");
        when(idpBindingSupport.lookupBinding(principal))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of("88"));
        when(idpPassportProvisioner.registerPassport(any(), any(), any()))
            .thenReturn(Result.error("conflict", "conflict"));

        assertEquals("88", service.resolvePassportId(principal, true));

        verify(idpBindingSupport, never()).saveBinding(anyLong(), any());
    }
}
