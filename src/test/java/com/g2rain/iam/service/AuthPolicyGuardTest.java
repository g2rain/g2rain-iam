package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.AuthPolicySource;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.LoginMethod;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthPolicyGuardTest {

    private final AuthPolicyGuard guard = new AuthPolicyGuard();

    @Test
    void requirePasswordRejectsWeComOnlyPolicy() {
        AuthorizationTransactionDto txn = txn(EnumSet.of(LoginMethod.WECOM), false);
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.requirePassword(txn));
        assertEquals(IamErrorCode.AUTH_POLICY_METHOD_DENIED.code(), ex.getErrorCode());
    }

    @Test
    void requireRegisterRejectsWhenDisabled() {
        AuthorizationTransactionDto txn = txn(EnumSet.of(LoginMethod.PASSWORD), false);
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.requireRegister(txn));
        assertEquals(IamErrorCode.AUTH_POLICY_REGISTER_DENIED.code(), ex.getErrorCode());
    }

    @Test
    void requireWeComRejectsBindModeMismatch() {
        AuthorizationTransactionDto txn = txn(EnumSet.of(LoginMethod.WECOM), false);
        txn.getAuthPolicy().setWeComBindMode("INTERNAL");
        BusinessException ex = assertThrows(
            BusinessException.class, () -> guard.requireWeCom(txn, "THIRD_PARTY"));
        assertEquals(IamErrorCode.AUTH_POLICY_METHOD_DENIED.code(), ex.getErrorCode());
    }

    private static AuthorizationTransactionDto txn(EnumSet<LoginMethod> methods, boolean allowRegister) {
        AuthPolicySnapshot snapshot = new AuthPolicySnapshot();
        snapshot.setLoginMethods(methods);
        snapshot.setAllowRegister(allowRegister);
        snapshot.setSource(AuthPolicySource.APPLICATION);
        AuthorizationTransactionDto txn = new AuthorizationTransactionDto();
        txn.setAuthPolicy(snapshot);
        return txn;
    }
}
