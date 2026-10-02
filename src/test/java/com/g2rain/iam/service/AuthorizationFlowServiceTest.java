package com.g2rain.iam.service;

import com.g2rain.basis.enums.ApplicationType;
import com.g2rain.basis.vo.ApplicationVo;
import com.g2rain.basis.vo.OrganIdNameVo;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.ApplicationAuthorizationClient;
import com.g2rain.iam.client.ApplicationClient;
import com.g2rain.iam.client.OrganClient;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.ConsentPreviewDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationFlowServiceTest {

    @Mock
    private AuthorizationTransactionService transactionService;
    @Mock
    private SessionService sessionService;
    @Mock
    private UserService userService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private IamAccessProperties iamAccessProperties;
    @Mock
    private DingTalkIamProperties dingTalkIamProperties;
    @Mock
    private WeComIamProperties weComIamProperties;
    @Mock
    private ApplicationAuthorizationClient applicationAuthorizationClient;
    @Mock
    private ApplicationClient applicationClient;
    @Mock
    private OrganClient organClient;

    private AuthorizationFlowService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationFlowService(
            transactionService,
            sessionService,
            userService,
            authorizationService,
            iamAccessProperties,
            dingTalkIamProperties,
            weComIamProperties,
            applicationAuthorizationClient,
            applicationClient,
            organClient
        );
    }

    @Test
    void continueFlow_withoutApplicationCode_autoIssuesCodeForSingleUser() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, null);
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));
        stubCompareAndUpdate(txn);
        when(authorizationService.generateAuthorizationCode(session, "client", "7", false))
            .thenReturn("legacy-code");

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertTrue(String.valueOf(mv.getViewName()).contains("code=legacy-code"));
        verify(authorizationService).generateAuthorizationCode(session, "client", "7", false);
        verify(applicationClient, never()).selectList(any());
    }

    @Test
    void continueFlow_withoutApplicationCode_multiUserShowsSelectPage() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, null);
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L), user(8L)));
        when(transactionService.get("tid-1")).thenReturn(txn);

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertEquals("consent", mv.getViewName());
        assertNull(mv.getModel().get("selectedUserId"));
        assertEquals("", mv.getModel().get("applicationCode"));
        verify(authorizationService, never()).generateAuthorizationCode(any(), any(), any(), anyBoolean());
        verify(applicationClient, never()).selectList(any());
    }

    @Test
    void confirm_withoutApplicationCode_issuesCodeForSelectedUser() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, null);
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L), user(8L)));
        stubCompareAndUpdate(txn);
        when(authorizationService.generateAuthorizationCode(session, "client", "8", false))
            .thenReturn("picked-code");

        ModelAndView mv = service.confirm(txn, "s1", "8", false);

        assertTrue(String.valueOf(mv.getViewName()).contains("code=picked-code"));
        verify(authorizationService).generateAuthorizationCode(session, "client", "8", false);
        verify(applicationClient, never()).selectList(any());
    }

    @Test
    void continueFlow_withPublicApplicationCode_loadsLocalPreview() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "open-app");
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        UserVo selected = user(7L);
        selected.setOrganId(100L);
        when(userService.listUserVos(session)).thenReturn(List.of(selected));
        stubCompareAndUpdate(txn);

        ApplicationVo application = application("开放应用", "应用说明", ApplicationType.PUBLIC.name());
        when(applicationClient.selectList(any())).thenReturn(Result.success(List.of(application)));
        when(organClient.selectOrganIdNameMap(any()))
            .thenReturn(Result.success(List.of(new OrganIdNameVo(100L, "租户甲"))));

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertEquals("consent", mv.getViewName());
        assertTrue(Boolean.TRUE.equals(txn.getOpenPlatformConsent()));
        ConsentPreviewDto preview = (ConsentPreviewDto) mv.getModel().get("preview");
        assertNotNull(preview);
        assertEquals("开放应用", preview.getApplicationName());
        assertEquals("应用说明", preview.getDescription());
        assertEquals("租户甲", preview.getOrganName());
        verify(authorizationService, never()).generateAuthorizationCode(
            any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void continueFlow_withSystemApplicationCode_issuesCodeWithoutConsent() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "page-app");
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));
        stubCompareAndUpdate(txn);
        when(applicationClient.selectList(any()))
            .thenReturn(Result.success(List.of(application("页面应用", "说明", ApplicationType.SYSTEM.name()))));
        when(authorizationService.generateAuthorizationCode(session, "client", "7", false))
            .thenReturn("page-code");

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertTrue(String.valueOf(mv.getViewName()).contains("code=page-code"));
        assertFalse(Boolean.TRUE.equals(txn.getOpenPlatformConsent()));
        assertEquals(Boolean.FALSE, txn.getOpenPlatformConsent());
        verify(authorizationService).generateAuthorizationCode(session, "client", "7", false);
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void continueFlow_withSupportApplicationCode_issuesCodeWithoutConsent() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "support-app");
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));
        stubCompareAndUpdate(txn);
        when(applicationClient.selectList(any()))
            .thenReturn(Result.success(List.of(application("支撑应用", "说明", ApplicationType.SUPPORT.name()))));
        when(authorizationService.generateAuthorizationCode(session, "client", "7", false))
            .thenReturn("support-code");

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertTrue(String.valueOf(mv.getViewName()).contains("code=support-code"));
        assertEquals(Boolean.FALSE, txn.getOpenPlatformConsent());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void continueFlow_withUnknownApplicationCode_failsClosed() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "missing-app");
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(applicationClient.selectList(any())).thenReturn(Result.success(List.of()));

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertEquals("error", mv.getViewName());
        verify(authorizationService, never()).generateAuthorizationCode(any(), any(), any(), anyBoolean());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void continueFlow_whenApplicationLookupThrows_failsClosed() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "boom-app");
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(applicationClient.selectList(any())).thenThrow(new RuntimeException("basis down"));

        ModelAndView mv = service.continueFlow(txn, "s1");

        assertEquals("error", mv.getViewName());
        verify(authorizationService, never()).generateAuthorizationCode(any(), any(), any(), anyBoolean());
    }

    @Test
    void confirm_denied_redirectsAccessDenied() {
        AuthorizationTransactionDto txn = txn(AuthorizationTransactionStatus.AUTHENTICATED, "open-app");
        stubCompareAndUpdate(txn);

        ModelAndView mv = service.confirm(txn, "s1", "7", true);

        assertTrue(String.valueOf(mv.getViewName()).contains("access_denied")
            || String.valueOf(mv.getViewName()).contains("redirect:"));
        verify(authorizationService, never()).generateAuthorizationCode(
            any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    private void stubCompareAndUpdate(AuthorizationTransactionDto txn) {
        when(transactionService.compareAndUpdate(eq("tid-1"), any(), any(), any())).thenAnswer(invocation -> {
            AuthorizationTransactionStatus next = invocation.getArgument(2);
            Consumer<AuthorizationTransactionDto> mutator = invocation.getArgument(3);
            if (mutator != null) {
                mutator.accept(txn);
            }
            txn.setStatus(next);
            return true;
        });
        when(transactionService.get("tid-1")).thenReturn(txn);
    }

    private static AuthorizationTransactionDto txn(AuthorizationTransactionStatus status, String applicationCode) {
        AuthorizationTransactionDto dto = new AuthorizationTransactionDto();
        dto.setTid("tid-1");
        dto.setClientId("client");
        dto.setRedirectUri("https://cb.example/cb");
        dto.setState("st");
        dto.setApplicationCode(applicationCode);
        dto.setStatus(status);
        dto.setSessionId("s1");
        dto.setFlowCookieHash("hash");
        return dto;
    }

    private static SessionDto session(String id) {
        SessionDto session = new SessionDto();
        session.setSessionId(id);
        return session;
    }

    private static UserVo user(long id) {
        UserVo user = new UserVo();
        user.setId(id);
        return user;
    }

    private static ApplicationVo application(String name, String description, String applicationType) {
        ApplicationVo application = new ApplicationVo();
        application.setApplicationName(name);
        application.setDescription(description);
        application.setApplicationType(applicationType);
        return application;
    }
}
