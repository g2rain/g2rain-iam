package com.g2rain.iam.config;

import com.g2rain.iam.enums.LoginMethod;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段完整的登录/注册策略（default 或某个 applicationCode 专条）。
 * <p>与其它条目互斥整段选用，禁止合并。</p>
 */
@Getter
@Setter
public class AuthPolicyEntry {

    /**
     * 本条目完整许可的登录方式；至少一种。
     */
    private List<LoginMethod> loginMethods = new ArrayList<>();

    /**
     * 钉钉 bindMode 覆盖；含 {@code DINGTALK} 时可选，空则回退全局 login-page-bind-mode。
     */
    private String dingTalkBindMode;

    /**
     * 企微 bindMode 覆盖；含 {@code WECOM} 时可选。
     */
    private String weComBindMode;

    /**
     * 是否允许注册。专条省略时为 {@code null}，解析为 {@code false}，不得回填 default。
     */
    private Boolean allowRegister;

    /**
     * 出厂平台 default：仅密码且允许注册。
     *
     * @return 新的 default 条目
     */
    public static AuthPolicyEntry platformDefault() {
        AuthPolicyEntry entry = new AuthPolicyEntry();
        entry.getLoginMethods().add(LoginMethod.PASSWORD);
        entry.setAllowRegister(Boolean.TRUE);
        return entry;
    }
}
