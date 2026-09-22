package com.g2rain.iam.service;

import com.g2rain.basis.dto.ApplicationAuthorizationActivateSelfRequest;
import com.g2rain.basis.dto.ApplicationSelectDto;
import com.g2rain.basis.vo.ApplicationAuthorizationActivateSelfVo;
import com.g2rain.basis.vo.ApplicationVo;
import com.g2rain.basis.vo.OrganIdNameVo;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.ApplicationAuthorizationClient;
import com.g2rain.iam.client.ApplicationClient;
import com.g2rain.iam.client.OrganClient;
import com.g2rain.iam.dto.ConsentPreviewDto;
import com.g2rain.iam.dto.SessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一 consent：单用户不自动发码、拒绝不发码、有 applicationCode 时 activate_self、无 code 走原 callback。
 */
class ModelAndViewServiceConsentTest {

    private ModelAndViewService service;
    private UserService userService;
    private SessionService sessionService;
    private AuthorizationService authorizationService;
    private ApplicationAuthorizationClient applicationAuthorizationClient;
    private ApplicationClient applicationClient;
    private OrganClient organClient;

    @BeforeEach
    void setUp() {
        service = new ModelAndViewService();
        userService = mock(UserService.class);
        sessionService = mock(SessionService.class);
        authorizationService = mock(AuthorizationService.class);
        applicationAuthorizationClient = mock(ApplicationAuthorizationClient.class);
        applicationClient = mock(ApplicationClient.class);
        organClient = mock(OrganClient.class);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "sessionService", sessionService);
        ReflectionTestUtils.setField(service, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(service, "applicationAuthorizationClient", applicationAuthorizationClient);
        ReflectionTestUtils.setField(service, "applicationClient", applicationClient);
        ReflectionTestUtils.setField(service, "organClient", organClient);
        ReflectionTestUtils.setField(service, "iamAccessProperties", mock(com.g2rain.iam.config.IamAccessProperties.class));
        ReflectionTestUtils.setField(service, "dingTalkIamProperties", mock(com.g2rain.iam.config.DingTalkIamProperties.class));
        ReflectionTestUtils.setField(service, "weComIamProperties", mock(com.g2rain.iam.config.WeComIamProperties.class));
    }

    @Test
    void redirectConsent_withoutApplicationCode_doesNotAutoIssueCodeForSingleUser() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));

        ModelAndView mv = service.redirectConsent("s1", "client", "https://cb.example/cb", "st");

        assertEquals("consent", mv.getViewName());
        assertEquals("7", mv.getModel().get("selectedUserId"));
        verify(authorizationService, never()).generateAuthorizationCode(any(), any(), any(), anyBoolean());
        verify(sessionService, never()).bindOAuthConsent(any(), any(), any(), any(), any());
        verify(applicationClient, never()).selectList(any());
    }

    @Test
    void redirectConsent_withApplicationCode_bindsSessionAndLoadsLocalPreview() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        UserVo selected = user(7L);
        selected.setOrganId(100L);
        when(userService.listUserVos(session)).thenReturn(List.of(selected));

        ApplicationVo application = new ApplicationVo();
        application.setApplicationName("开放应用");
        application.setDescription("应用说明");
        when(applicationClient.selectList(any(ApplicationSelectDto.class)))
            .thenReturn(Result.success(List.of(application)));
        when(organClient.selectOrganIdNameMap(any()))
            .thenReturn(Result.success(List.of(new OrganIdNameVo(100L, "租户甲"))));

        ModelAndView mv = service.redirectConsent(
            "s1", "client", "https://cb.example/cb", "st", "open-app");

        assertEquals("consent", mv.getViewName());
        ConsentPreviewDto preview = (ConsentPreviewDto) mv.getModel().get("preview");
        assertNotNull(preview);
        assertEquals("开放应用", preview.getApplicationName());
        assertEquals("应用说明", preview.getDescription());
        assertEquals("租户甲", preview.getOrganName());
        verify(sessionService).bindOAuthConsent(
            "s1", "client", "https://cb.example/cb", "open-app", "st");
        verify(authorizationService, never()).generateAuthorizationCode(
            any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void confirmConsent_denied_clearsBindingAndDoesNotIssueCode() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);

        ModelAndView mv = service.confirmConsent(
            "s1", "7", "client", "https://cb.example/cb", "st", "open-app", true);

        assertTrue(String.valueOf(mv.getViewName()).contains("access_denied")
            || String.valueOf(mv.getViewName()).contains("redirect:"));
        verify(sessionService).clearOAuthConsent("s1");
        verify(authorizationService, never()).generateAuthorizationCode(
            any(), any(), any(), anyBoolean(), any(), any(), any());
        verify(applicationAuthorizationClient, never()).activateSelf(any());
    }

    @Test
    void confirmConsent_withApplicationCode_activatesSelfThenIssuesCode() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));
        when(sessionService.requireOAuthConsentMatching("s1", "client", "https://cb.example/cb", "open-app"))
            .thenReturn(session);

        ApplicationAuthorizationActivateSelfVo activated = new ApplicationAuthorizationActivateSelfVo();
        activated.setApplicationId(88L);
        activated.setOrganId(100L);
        when(applicationAuthorizationClient.activateSelf(any()))
            .thenReturn(Result.success(activated));
        when(authorizationService.generateAuthorizationCode(
            eq(session), eq("client"), eq("7"), eq(false),
            eq("open-app"), eq(88L), eq(100L)))
            .thenReturn("code-1");

        ModelAndView mv = service.confirmConsent(
            "s1", "7", "client", "https://cb.example/cb", "st", "open-app", false);

        assertTrue(String.valueOf(mv.getViewName()).contains("code=code-1"));
        ArgumentCaptor<ApplicationAuthorizationActivateSelfRequest> captor =
            ArgumentCaptor.forClass(ApplicationAuthorizationActivateSelfRequest.class);
        verify(applicationAuthorizationClient).activateSelf(captor.capture());
        assertEquals("open-app", captor.getValue().getApplicationCode());
        assertEquals(7L, captor.getValue().getUserId());
        verify(sessionService).clearOAuthConsent("s1");
    }

    @Test
    void confirmConsent_withoutApplicationCode_usesLegacyCallback() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));
        when(authorizationService.generateAuthorizationCode(session, "client", "7", false))
            .thenReturn("legacy-code");

        ModelAndView mv = service.confirmConsent(
            "s1", "7", "client", "https://cb.example/cb", "st", null, false);

        assertTrue(String.valueOf(mv.getViewName()).contains("code=legacy-code"));
        verify(applicationAuthorizationClient, never()).activateSelf(any());
        verify(sessionService, never()).requireOAuthConsentMatching(any(), any(), any(), any());
        verify(authorizationService).generateAuthorizationCode(
            eq(session), eq("client"), eq("7"), eq(false));
        verify(authorizationService, never()).generateAuthorizationCode(
            any(), any(), any(), anyBoolean(), anyString(), any(), any());
    }

    @Test
    void redirectCallback_rejectsBoundUser() {
        SessionDto session = session("s1");
        when(sessionService.getSession("s1")).thenReturn(session);
        when(userService.listUserVos(session)).thenReturn(List.of(user(7L)));

        ModelAndView mv = service.redirectCallback(
            "s1", "99", "client", "https://cb.example/cb", "st");
        assertEquals("error", mv.getViewName());
        verify(authorizationService, never()).generateAuthorizationCode(any(), any(), any(), anyBoolean());
    }

    private static SessionDto session(String id) {
        SessionDto session = new SessionDto();
        session.setSessionId(id);
        return session;
    }

    private static UserVo user(Long id) {
        UserVo vo = new UserVo();
        vo.setId(id);
        vo.setRealName("admin");
        return vo;
    }
}
