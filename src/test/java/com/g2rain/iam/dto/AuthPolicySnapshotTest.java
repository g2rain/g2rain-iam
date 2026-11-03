package com.g2rain.iam.dto;

import com.g2rain.iam.enums.LoginMethod;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPolicySnapshotTest {

    @Test
    void allowsReturnsTrueOnlyForFrozenMethods() {
        AuthPolicySnapshot snapshot = new AuthPolicySnapshot();
        snapshot.setLoginMethods(EnumSet.of(LoginMethod.PASSWORD, LoginMethod.WECOM));

        assertTrue(snapshot.allows(LoginMethod.PASSWORD));
        assertTrue(snapshot.allows(LoginMethod.WECOM));
        assertFalse(snapshot.allows(LoginMethod.DINGTALK));
        assertFalse(snapshot.allows(null));
    }

    @Test
    void allowsFailsClosedWhenMethodsAreMissing() {
        AuthPolicySnapshot snapshot = new AuthPolicySnapshot();
        snapshot.setLoginMethods(null);

        assertFalse(snapshot.allows(LoginMethod.PASSWORD));
    }
}
