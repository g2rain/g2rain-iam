package com.g2rain.iam.controller;

import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.SessionService;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageControllerSessionTest {

    private static final String PLATFORM_HOME = Constants.REDIRECT + "https://platform.example.com/main/home";

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @Mock
    private IamAccessProperties iamAccessProperties;

    @Mock
    private DingTalkIamProperties dingTalkIamProperties;

    @Mock
    private WeComIamProperties weComIamProperties;

    @Mock
    private SessionService sessionService;

    @Mock
    private ModelAndViewService modelAndViewService;

    @InjectMocks
    private PageController pageController;

    @BeforeEach
    void setUpResource() {
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.isReadable()).thenReturn(true);
    }

    @Test
    void indexWithoutSessionRedirectsToPlatformMainHome() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        when(modelAndViewService.redirectPlatformMainHome())
            .thenReturn(new ModelAndView(PLATFORM_HOME));

        ModelAndView view = pageController.dynamicPage(
            "index", null, null, null, null, null, request, model);

        assertEquals(PLATFORM_HOME, view.getViewName());
        verify(modelAndViewService).redirectPlatformMainHome();
    }

    @Test
    void indexWithoutSessionFromLogoutRendersGuestIndex() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        when(iamAccessProperties.resolvedPlatformBaseUrl()).thenReturn("https://platform.example.com");

        ModelAndView view = pageController.dynamicPage(
            "index", null, null, null, "logout", null, request, model);

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

        ModelAndView view = pageController.dynamicPage(
            "index", null, null, null, null, "session-1", request, model);

        assertEquals("index", view.getViewName());
        assertEquals(true, model.get("loggedIn"));
        assertEquals("Alice", model.get("accountName"));
        assertEquals("10001", model.get("passportId"));
        assertEquals("账号密码", model.get("loginMethod"));
    }

    @Test
    void loginWithSessionWithoutOAuthRedirectsToIndex() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.SESSION_NAME, "session-2"));
        SessionDto session = new SessionDto();
        session.setSessionId("session-2");
        when(sessionService.getSession("session-2")).thenReturn(session);
        ExtendedModelMap model = new ExtendedModelMap();

        ModelAndView view = pageController.dynamicPage(
            "login", null, null, null, null, "session-2", request, model);

        assertEquals(Constants.REDIRECT + "/auth/index.html", view.getViewName());
    }

    @Test
    void loginWithSessionAndOAuthUsesConsentFlow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SessionDto session = new SessionDto();
        session.setSessionId("session-3");
        when(sessionService.getSession("session-3")).thenReturn(session);
        ModelAndView consentView = new ModelAndView("consent");
        when(modelAndViewService.redirectConsent(
            eq("session-3"), eq("client-a"), eq("https://app.test/callback"), eq("state-x")))
            .thenReturn(consentView);
        ExtendedModelMap model = new ExtendedModelMap();

        ModelAndView view = pageController.dynamicPage(
            "login",
            "https://app.test/callback",
            "client-a",
            "state-x",
            null,
            "session-3",
            request,
            model);

        assertEquals("consent", view.getViewName());
        verify(modelAndViewService).redirectConsent(
            "session-3", "client-a", "https://app.test/callback", "state-x");
    }

    @Test
    void indexWithoutSessionWithOAuthRedirectsToLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();

        ModelAndView view = pageController.dynamicPage(
            "index",
            "https://app.test/callback",
            "client-a",
            "state-x",
            null,
            null,
            request,
            model);

        assertTrue(view.getViewName().startsWith(Constants.REDIRECT));
        assertTrue(view.getViewName().contains("clientId=client-a"));
        assertTrue(view.getViewName().contains("redirectUri="));
        assertTrue(view.getViewName().contains("state=state-x"));
    }

    @Test
    void loginWithoutOAuthRedirectsToPlatformMainHome() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        when(modelAndViewService.redirectPlatformMainHome())
            .thenReturn(new ModelAndView(PLATFORM_HOME));

        ModelAndView view = pageController.dynamicPage(
            "login", null, null, null, null, null, request, model);

        assertEquals(PLATFORM_HOME, view.getViewName());
        verify(modelAndViewService).redirectPlatformMainHome();
    }

    @Test
    void loginWithOAuthNoSessionRendersLoginPage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();

        ModelAndView view = pageController.dynamicPage(
            "login",
            "https://app.test/callback",
            "client-a",
            "state-x",
            null,
            null,
            request,
            model);

        assertEquals("login", view.getViewName());
        assertEquals("client-a", model.get("clientId"));
        assertEquals("https://app.test/callback", model.get("redirectUri"));
    }
}
