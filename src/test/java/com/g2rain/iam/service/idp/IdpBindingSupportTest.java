package com.g2rain.iam.service.idp;

import com.g2rain.basis.idp.resolve.dto.IdpPassportResolveRequest;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.idp.IdpPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdpBindingSupportTest {

    @Test
    void hasIdpContext_shouldRequireTypeAndSubject() {
        SessionDto session = new SessionDto();
        assertFalse(IdpBindingSupport.hasIdpContext(session));

        session.setIdpType("DINGTALK");
        assertFalse(IdpBindingSupport.hasIdpContext(session));

        session.setIdpSubject("union-1");
        assertTrue(IdpBindingSupport.hasIdpContext(session));
    }

    @Test
    void buildResolveRequest_shouldMapPrincipalFields() {
        IdpPrincipal principal = new IdpPrincipal(
            "DINGTALK",
            "union-1",
            "userid-1",
            "open-1",
            "corp-1",
            "张三",
            "INTERNAL",
            "{}",
            " client-1 "
        );

        IdpPassportResolveRequest request = IdpBindingSupport.buildResolveRequest(principal);

        assertEquals("DINGTALK", request.getIdpType());
        assertEquals("union-1", request.getIdpSubject());
        assertEquals("client-1", request.getIdpApplicationCode());
        assertEquals("userid-1", request.getIdpUserId());
    }

    @Test
    void buildResolveRequest_shouldMapSessionFields() {
        SessionDto session = new SessionDto();
        session.setIdpType("DINGTALK");
        session.setIdpSubject("union-1");
        session.setIdpApplicationCode(" client-1 ");
        session.setIdpUserId(" userid-1 ");

        IdpPassportResolveRequest request = IdpBindingSupport.buildResolveRequest(session);

        assertEquals("DINGTALK", request.getIdpType());
        assertEquals("union-1", request.getIdpSubject());
        assertEquals("client-1", request.getIdpApplicationCode());
        assertEquals("userid-1", request.getIdpUserId());
    }

    @Test
    void buildResolveRequest_shouldOmitBlankSessionIdpUserId() {
        SessionDto session = new SessionDto();
        session.setIdpType("DINGTALK");
        session.setIdpSubject("union-1");
        session.setIdpApplicationCode("client-1");
        session.setIdpUserId("  ");

        IdpPassportResolveRequest request = IdpBindingSupport.buildResolveRequest(session);

        assertNull(request.getIdpUserId());
    }
}
