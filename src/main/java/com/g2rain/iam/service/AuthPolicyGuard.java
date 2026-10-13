package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.LoginMethod;
import org.springframework.stereotype.Service;

/**
 * 对冻结策略做服务端闸门（隐藏 UI 不算安全）。
 */
@Service
public class AuthPolicyGuard {

    /**
     * 要求冻结策略允许账号密码登录。
     *
     * @param txn 授权事务
     */
    public void requirePassword(AuthorizationTransactionDto txn) {
        requireMethod(txn, LoginMethod.PASSWORD);
    }

    /**
     * 要求冻结策略允许注册。
     *
     * @param txn 授权事务
     */
    public void requireRegister(AuthorizationTransactionDto txn) {
        AuthPolicySnapshot snapshot = snapshot(txn);
        if (!snapshot.isAllowRegister()) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_REGISTER_DENIED);
        }
    }

    /**
     * 要求冻结策略允许钉钉登录，且请求 bindMode 与冻结值一致。
     *
     * @param txn      授权事务
     * @param bindMode 请求中的 bindMode
     */
    public void requireDingTalk(AuthorizationTransactionDto txn, String bindMode) {
        requireMethod(txn, LoginMethod.DINGTALK);
        requireBindMode(snapshot(txn).getDingTalkBindMode(), bindMode);
    }

    /**
     * 要求冻结策略允许企微登录，且请求 bindMode 与冻结值一致。
     *
     * @param txn      授权事务
     * @param bindMode 请求中的 bindMode
     */
    public void requireWeCom(AuthorizationTransactionDto txn, String bindMode) {
        requireMethod(txn, LoginMethod.WECOM);
        requireBindMode(snapshot(txn).getWeComBindMode(), bindMode);
    }

    private void requireMethod(AuthorizationTransactionDto txn, LoginMethod method) {
        if (!snapshot(txn).allows(method)) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_METHOD_DENIED);
        }
    }

    private static AuthPolicySnapshot snapshot(AuthorizationTransactionDto txn) {
        if (txn == null || txn.getAuthPolicy() == null) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_NO_LOGIN_METHOD);
        }
        return txn.getAuthPolicy();
    }

    private static void requireBindMode(String frozen, String requested) {
        if (Strings.isBlank(frozen) || Strings.isBlank(requested)) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_METHOD_DENIED);
        }
        if (!frozen.trim().equalsIgnoreCase(requested.trim())) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_METHOD_DENIED);
        }
    }
}
