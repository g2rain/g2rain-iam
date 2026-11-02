package com.g2rain.iam.dto;

import com.g2rain.iam.enums.AuthPolicySource;
import com.g2rain.iam.enums.LoginMethod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 授权事务内冻结的登录/注册策略快照（已与平台 IdP 能力求交）。
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthPolicySnapshot {

    /**
     * 生效登录方式（非空）。
     */
    private Set<LoginMethod> loginMethods = EnumSet.noneOf(LoginMethod.class);

    /**
     * 生效钉钉 bindMode；无钉钉则为空。
     */
    private String dingTalkBindMode;

    /**
     * 生效企微 bindMode；无企微则为空。
     */
    private String weComBindMode;

    /**
     * 是否允许 Passport 自助注册。
     */
    private boolean allowRegister;

    /**
     * 策略来源：专条或平台 default。
     */
    private AuthPolicySource source;

    /**
     * 是否包含指定登录方式。
     *
     * @param method 登录方式
     * @return 包含则为 {@code true}
     */
    public boolean allows(LoginMethod method) {
        return method != null && loginMethods != null && loginMethods.contains(method);
    }
}
