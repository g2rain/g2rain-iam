package com.g2rain.iam.service;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.idp.IdpPrincipal;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * IAM 会话服务：统一管理 {@link RedisKeyRule#SESSION} 的创建、读取与销毁。
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    static final Duration SESSION_TTL = Duration.ofHours(24);

    private final GenericRedisHelper genericRedisHelper;

    /**
     * 为账号密码登录创建会话
     */
    public String createPassportSession(String passportId, String name) {
        String sessionId = IamUtils.generateSessionId();
        SessionDto session = new SessionDto();
        session.setSessionId(sessionId);
        session.setPassportId(passportId);
        session.setName(name);
        persist(session);
        return sessionId;
    }

    /**
     * 为 IdP 登录创建会话（写入 IdP 元数据）
     */
    public String createIdpSession(String passportId, IdpPrincipal principal) {
        return createIdpSession(passportId, principal, IdpLoginRole.USER, false);
    }

    /**
     * 为 IdP 员工扫码登录创建会话
     */
    public String createIdpSession(
        String passportId, IdpPrincipal principal, IdpLoginRole loginRole, boolean idpAdmin) {
        String sessionId = IamUtils.generateSessionId();
        String idpApplicationCode = principal.idpApplicationCode() == null
            ? ""
            : principal.idpApplicationCode().trim();

        SessionDto session = new SessionDto();
        session.setSessionId(sessionId);
        session.setPassportId(passportId);
        session.setName(Strings.isBlank(principal.displayName()) ? null : principal.displayName());
        session.setIdpType(principal.idpType());
        session.setIdpSubject(principal.idpSubject());
        session.setIdpBindMode(principal.bindMode());
        session.setIdpApplicationCode(idpApplicationCode);
        session.setIdpUserId(principal.idpUserId());
        session.setIdpLoginRole(loginRole == null ? IdpLoginRole.USER.name() : loginRole.name());
        session.setIdpAdmin(idpAdmin);
        persist(session);
        if (idpAdmin && Strings.isNotBlank(passportId)) {
            genericRedisHelper.set(
                RedisKeyRule.IDP_TENANT_PROVISION_ELIGIBLE.format(passportId.trim()),
                "1",
                SESSION_TTL
            );
        }
        return sessionId;
    }

    public SessionDto getSession(String sessionId) {
        if (Objects.isNull(sessionId)) {
            return null;
        }
        return genericRedisHelper.get(
            RedisKeyRule.SESSION.format(sessionId),
            SessionDto.class
        );
    }

    public boolean isSessionExpired(String sessionId) {
        return Objects.isNull(getSession(sessionId));
    }

    public void logout(String sessionId) {
        if (Objects.nonNull(sessionId)) {
            genericRedisHelper.delete(RedisKeyRule.SESSION.format(sessionId));
        }
    }

    /**
     * 有 {@code applicationCode} 时将会话与待确认 OAuth 参数写入 {@link SessionDto} 并持久化到 Redis。
     * <p>用于 consent 确认时防篡改：{@link #requireOAuthConsentMatching} 校验表单与绑定一致。</p>
     */
    public void bindOAuthConsent(
        String sessionId, String clientId, String redirectUri, String applicationCode, String state) {
        if (Strings.isBlank(applicationCode)) {
            return;
        }
        SessionDto session = getSession(sessionId);
        if (session == null) {
            throw new BusinessException(IamErrorCode.OAUTH_CONSENT_SESSION_INVALID);
        }
        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            throw new BusinessException(IamErrorCode.OAUTH_CONSENT_SESSION_INVALID);
        }
        session.setOauthClientId(clientId.trim());
        session.setOauthRedirectUri(redirectUri.trim());
        session.setOauthApplicationCode(applicationCode.trim());
        session.setOauthState(state);
        persist(session);
    }

    /**
     * 确认时校验会话绑定的 OAuth 参数与表单一致。
     */
    public SessionDto requireOAuthConsentMatching(
        String sessionId, String clientId, String redirectUri, String applicationCode) {
        SessionDto session = getSession(sessionId);
        if (session == null
            || Strings.isBlank(session.getOauthApplicationCode())
            || !Objects.equals(blankToNull(session.getOauthClientId()), blankToNull(clientId))
            || !Objects.equals(blankToNull(session.getOauthRedirectUri()), blankToNull(redirectUri))
            || !Objects.equals(blankToNull(session.getOauthApplicationCode()), blankToNull(applicationCode))) {
            throw new BusinessException(IamErrorCode.OAUTH_CONSENT_SESSION_INVALID);
        }
        return session;
    }

    /**
     * 发码或拒绝后清空待确认 OAuth 绑定。
     */
    public void clearOAuthConsent(String sessionId) {
        SessionDto session = getSession(sessionId);
        if (session == null) {
            return;
        }
        session.setOauthClientId(null);
        session.setOauthRedirectUri(null);
        session.setOauthApplicationCode(null);
        session.setOauthState(null);
        persist(session);
    }

    private void persist(SessionDto session) {
        genericRedisHelper.set(
            RedisKeyRule.SESSION.format(session.getSessionId()),
            session,
            SESSION_TTL
        );
    }

    private static String blankToNull(String value) {
        return Strings.isBlank(value) ? null : value.trim();
    }
}
