package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.AuthPolicyEntry;
import com.g2rain.iam.config.AuthPolicyProperties;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.AuthPolicySnapshot;
import com.g2rain.iam.enums.AuthPolicySource;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.LoginMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按 applicationCode 互斥选用登录策略，并与平台 IdP 能力求交后冻结。
 */
@Service
@RequiredArgsConstructor
public class AuthPolicyResolver {

    private final AuthPolicyProperties authPolicyProperties;
    private final DingTalkIamProperties dingTalkIamProperties;
    private final WeComIamProperties weComIamProperties;

    /**
     * 解析生效策略。无 code 或 Map 未命中则整段使用 default；命中专条则不再读 default。
     *
     * @param applicationCode 授权请求中的应用编码，可空
     * @return 已求交的冻结快照
     */
    public AuthPolicySnapshot resolve(String applicationCode) {
        String code = Strings.isBlank(applicationCode) ? null : applicationCode.trim();
        AuthPolicyEntry mapped = code == null ? null : findApplicationEntry(code);
        AuthPolicySource source = mapped == null ? AuthPolicySource.PLATFORM_DEFAULT : AuthPolicySource.APPLICATION;
        AuthPolicyEntry entry = mapped == null ? authPolicyProperties.getDefault() : mapped;

        Set<LoginMethod> methods = copyMethods(entry);
        if (source == AuthPolicySource.PLATFORM_DEFAULT && methods.isEmpty()) {
            methods.add(LoginMethod.PASSWORD);
        }

        String dingTalkBindMode = null;
        if (methods.contains(LoginMethod.DINGTALK)) {
            dingTalkBindMode = firstNonBlank(entry.getDingTalkBindMode(), dingTalkIamProperties.getLoginPageBindMode());
            if (!idpCapable(LoginMethod.DINGTALK, dingTalkBindMode)) {
                methods.remove(LoginMethod.DINGTALK);
                dingTalkBindMode = null;
            } else {
                dingTalkBindMode = normalizeBindMode(dingTalkBindMode);
            }
        }

        String weComBindMode = null;
        if (methods.contains(LoginMethod.WECOM)) {
            weComBindMode = firstNonBlank(entry.getWeComBindMode(), weComIamProperties.getLoginPageBindMode());
            if (!idpCapable(LoginMethod.WECOM, weComBindMode)) {
                methods.remove(LoginMethod.WECOM);
                weComBindMode = null;
            } else {
                weComBindMode = normalizeBindMode(weComBindMode);
            }
        }

        if (methods.isEmpty()) {
            throw new BusinessException(IamErrorCode.AUTH_POLICY_NO_LOGIN_METHOD);
        }

        boolean allowRegister;
        if (source == AuthPolicySource.APPLICATION) {
            allowRegister = Boolean.TRUE.equals(entry.getAllowRegister());
        } else {
            allowRegister = entry.getAllowRegister() == null || Boolean.TRUE.equals(entry.getAllowRegister());
        }

        AuthPolicySnapshot snapshot = new AuthPolicySnapshot();
        snapshot.setLoginMethods(methods);
        snapshot.setDingTalkBindMode(dingTalkBindMode);
        snapshot.setWeComBindMode(weComBindMode);
        snapshot.setAllowRegister(allowRegister);
        snapshot.setSource(source);
        return snapshot;
    }

    private AuthPolicyEntry findApplicationEntry(String applicationCode) {
        Map<String, AuthPolicyEntry> applications = authPolicyProperties.getApplications();
        AuthPolicyEntry direct = applications.get(applicationCode);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, AuthPolicyEntry> item : applications.entrySet()) {
            if (item.getKey() != null && applicationCode.equals(item.getKey().trim())) {
                return item.getValue();
            }
        }
        return null;
    }

    private static Set<LoginMethod> copyMethods(AuthPolicyEntry entry) {
        Set<LoginMethod> methods = EnumSet.noneOf(LoginMethod.class);
        if (entry.getLoginMethods() != null) {
            for (LoginMethod method : entry.getLoginMethods()) {
                if (method != null) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private boolean idpCapable(LoginMethod method, String bindMode) {
        String normalized = normalizeBindMode(bindMode);
        if (normalized == null) {
            return false;
        }
        if (method == LoginMethod.DINGTALK) {
            DingTalkIamProperties.Credential credential = "INTERNAL".equals(normalized)
                ? dingTalkIamProperties.getInternal()
                : dingTalkIamProperties.getThirdParty();
            return credential != null
                && Strings.isNotBlank(credential.getClientId())
                && Strings.isNotBlank(credential.getClientSecret());
        }
        if (method == LoginMethod.WECOM) {
            if ("INTERNAL".equals(normalized)) {
                WeComIamProperties.Internal internal = weComIamProperties.getInternal();
                return internal != null
                    && Strings.isNotBlank(internal.getCorpId())
                    && Strings.isNotBlank(internal.getAgentId())
                    && Strings.isNotBlank(internal.getSecret());
            }
            WeComIamProperties.ThirdParty thirdParty = weComIamProperties.getThirdParty();
            return thirdParty != null
                && Strings.isNotBlank(thirdParty.getSuiteId())
                && Strings.isNotBlank(thirdParty.getSuiteSecret());
        }
        return false;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (Strings.isNotBlank(preferred)) {
            return preferred.trim();
        }
        if (Strings.isNotBlank(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private static String normalizeBindMode(String bindMode) {
        if (Strings.isBlank(bindMode)) {
            return null;
        }
        String normalized = bindMode.trim().toUpperCase(Locale.ROOT);
        if ("INTERNAL".equals(normalized) || "THIRD_PARTY".equals(normalized)) {
            return normalized;
        }
        return null;
    }
}
