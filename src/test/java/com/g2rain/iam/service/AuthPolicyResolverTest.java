package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.config.AuthPolicyEntry;
import com.g2rain.iam.config.AuthPolicyProperties;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.enums.AuthPolicySource;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.LoginMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPolicyResolverTest {

    private AuthPolicyProperties properties;
    private DingTalkIamProperties dingTalkIamProperties;
    private WeComIamProperties weComIamProperties;
    private AuthPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new AuthPolicyProperties();
        dingTalkIamProperties = new DingTalkIamProperties();
        weComIamProperties = new WeComIamProperties();
        weComIamProperties.setLoginPageBindMode("INTERNAL");
        weComIamProperties.getInternal().setCorpId("corp");
        weComIamProperties.getInternal().setAgentId("100");
        weComIamProperties.getInternal().setSecret("secret");
        resolver = new AuthPolicyResolver(properties, dingTalkIamProperties, weComIamProperties);
    }

    @Test
    void blankCodeUsesDefaultPasswordAndRegister() {
        AuthPolicySnapshot snapshot = resolver.resolve(null);
        assertEquals(AuthPolicySource.PLATFORM_DEFAULT, snapshot.getSource());
        assertEquals(EnumSet.of(LoginMethod.PASSWORD), snapshot.getLoginMethods());
        assertTrue(snapshot.isAllowRegister());
        assertFalse(snapshot.allows(LoginMethod.WECOM));
    }

    @Test
    void unregisteredOpenPlatformCodeUsesDefault() {
        AuthPolicySnapshot snapshot = resolver.resolve("customer-public-app");
        assertEquals(AuthPolicySource.PLATFORM_DEFAULT, snapshot.getSource());
        assertTrue(snapshot.allows(LoginMethod.PASSWORD));
        assertTrue(snapshot.isAllowRegister());
    }

    @Test
    void mainShellUsesDedicatedEntryWithoutMergingDefaultRegister() {
        AuthPolicyEntry entry = new AuthPolicyEntry();
        entry.setLoginMethods(List.of(LoginMethod.PASSWORD, LoginMethod.WECOM));
        entry.setWeComBindMode("INTERNAL");
        entry.setAllowRegister(false);
        properties.getApplications().put("g2rain-main-shell", entry);

        AuthPolicySnapshot snapshot = resolver.resolve("g2rain-main-shell");
        assertEquals(AuthPolicySource.APPLICATION, snapshot.getSource());
        assertTrue(snapshot.allows(LoginMethod.PASSWORD));
        assertTrue(snapshot.allows(LoginMethod.WECOM));
        assertEquals("INTERNAL", snapshot.getWeComBindMode());
        assertFalse(snapshot.isAllowRegister());
    }

    @Test
    void adminShellOnlyWeComDoesNotInheritDefaultPassword() {
        AuthPolicyEntry entry = new AuthPolicyEntry();
        entry.setLoginMethods(List.of(LoginMethod.WECOM));
        entry.setWeComBindMode("INTERNAL");
        properties.getApplications().put("g2rain-admin-shell", entry);

        AuthPolicySnapshot snapshot = resolver.resolve("g2rain-admin-shell");
        assertEquals(AuthPolicySource.APPLICATION, snapshot.getSource());
        assertEquals(EnumSet.of(LoginMethod.WECOM), snapshot.getLoginMethods());
        assertFalse(snapshot.allows(LoginMethod.PASSWORD));
        assertFalse(snapshot.isAllowRegister());
    }

    @Test
    void declaredIdpWithoutCapabilityIsRemovedAndFailsWhenEmpty() {
        AuthPolicyEntry entry = new AuthPolicyEntry();
        entry.setLoginMethods(List.of(LoginMethod.DINGTALK));
        entry.setDingTalkBindMode("INTERNAL");
        properties.getApplications().put("shell-x", entry);
        dingTalkIamProperties.setLoginPageBindMode("");

        BusinessException ex = assertThrows(BusinessException.class, () -> resolver.resolve("shell-x"));
        assertEquals(IamErrorCode.AUTH_POLICY_NO_LOGIN_METHOD.code(), ex.getErrorCode());
    }
}
