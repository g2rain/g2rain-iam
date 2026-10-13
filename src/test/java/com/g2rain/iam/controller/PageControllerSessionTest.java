package com.g2rain.iam.controller;

import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.SessionService;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageControllerSessionTest {

    private static final String PLATFORM_HOME = Constants.REDIRECT + "https://platform.example.com/main/home";

    @Mock
    private IamAccessProperties iamAccessProperties;

    @Mock
    private SessionService sessionService;

    @Mock
    private ModelAndViewService modelAndViewService;

    @Mock
    private AuthorizationTransactionService transactionService;

    @Mock
    private AuthFlowCookieService authFlowCookieService;

    @Mock
    private AuthorizationFlowService authorizationFlowService;

    @InjectMocks
    private PageController pageController;

    @Test
    void indexWithoutSessionRedirectsToPlatformMainHome() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        when(modelAndViewService.redirectPlatformMainHome())
            .thenReturn(new ModelAndView(PLATFORM_HOME));

        ModelAndView view = pageController.indexPage(
            null, null, null, request, model);

        assertEquals(PLATFORM_HOME, view.getViewName());
        verify(modelAndViewService).redirectPlatformMainHome();
    }

    @Test
    void platformEndpointRedirectsThroughServerSidePlatformResolver() {
        when(modelAndViewService.redirectPlatformMainHome())
            .thenReturn(new ModelAndView(PLATFORM_HOME));

        ModelAndView view = pageController.redirectToPlatform();

        assertEquals(PLATFORM_HOME, view.getViewName());
        verify(modelAndViewService).redirectPlatformMainHome();
    }

    @Test
    void indexWithoutSessionFromLogoutRendersGuestIndex() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        when(iamAccessProperties.resolvedPlatformBaseUrl()).thenReturn("https://platform.example.com");

        ModelAndView view = pageController.indexPage(
            null, "logout", null, request, model);

        assertEquals("index", view.getViewName());
        assertFalse((Boolean) model.get("loggedIn"));
        assertEquals("https://platform.example.com", model.get("platformBaseUrl"));
        assertFalse((Boolean) model.get("loginViaRedirectUri"));
    }

    @Test
    void indexWithSessionRendersAccountModel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.SESSION_NAME, "session-1"));
        SessionDto session = new SessionDto();
        session.setSessionId("session-1");
        session.setPassportId("10001");
        session.setName("Alice");
        when(sessionService.getSession("session-1")).thenReturn(session);
        when(iamAccessProperties.resolvedPlatformBaseUrl()).thenReturn("https://iam.example.com");
        ExtendedModelMap model = new ExtendedModelMap();

        ModelAndView view = pageController.indexPage(
            null, null, "session-1", request, model);

        assertEquals("index", view.getViewName());
        assertEquals(true, model.get("loggedIn"));
        assertEquals("Alice", model.get("accountName"));
        assertEquals("10001", model.get("passportId"));
        assertEquals("账号密码", model.get("loginMethod"));
    }

    @Test
    void loginWithoutTidRendersFlowError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(authorizationFlowService.renderFlowError(null, "请从业务侧重新发起授权后再登录"))
            .thenReturn(new ModelAndView("error"));

        ModelAndView view = pageController.loginPage(request, null, null, "session-2");

        assertEquals("error", view.getViewName());
        verify(authorizationFlowService).renderFlowError(null, "请从业务侧重新发起授权后再登录");
    }

    @Test
    void loginWithTidRendersLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthorizationTransactionDto txn = new AuthorizationTransactionDto();
        txn.setTid("tid-1");
        when(authFlowCookieService.readRaw(request)).thenReturn("flow");
        when(authFlowCookieService.hash("flow")).thenReturn("hash");
        when(transactionService.requireActive("tid-1", "hash")).thenReturn(txn);
        when(authorizationFlowService.renderLogin(txn, null, null, "wecom"))
            .thenReturn(new ModelAndView("login"));

        ModelAndView view = pageController.loginPage(request, "tid-1", "wecom", null);

        assertEquals("login", view.getViewName());
        verify(authorizationFlowService).renderLogin(txn, null, null, "wecom");
    }

    @Test
    void loginWithTidAndSessionContinuesFlow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthorizationTransactionDto txn = new AuthorizationTransactionDto();
        txn.setTid("tid-2");
        SessionDto session = new SessionDto();
        session.setSessionId("session-2");
        when(sessionService.getSession("session-2")).thenReturn(session);
        when(authFlowCookieService.readRaw(request)).thenReturn("flow");
        when(authFlowCookieService.hash("flow")).thenReturn("hash");
        when(transactionService.requireActive("tid-2", "hash")).thenReturn(txn);
        when(authorizationFlowService.continueFlow(txn, "session-2"))
            .thenReturn(new ModelAndView(Constants.REDIRECT + "/auth/authorize?tid=tid-2"));

        ModelAndView view = pageController.loginPage(request, "tid-2", null, "session-2");

        assertTrue(view.getViewName().contains("/auth/authorize"));
        verify(authorizationFlowService).continueFlow(txn, "session-2");
    }

    @Test
    void registerWithoutTidRendersFlowError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(authorizationFlowService.renderFlowError(null, "请从业务侧重新发起授权后再注册"))
            .thenReturn(new ModelAndView("error"));

        ModelAndView view = pageController.registerPage(request, null);

        assertEquals("error", view.getViewName());
        verify(authorizationFlowService).renderFlowError(null, "请从业务侧重新发起授权后再注册");
    }
}
