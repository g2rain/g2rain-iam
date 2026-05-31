package com.g2rain.iam.service;


import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.RedisKeyRule;
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
        persist(session);
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

    private void persist(SessionDto session) {
        genericRedisHelper.set(
            RedisKeyRule.SESSION.format(session.getSessionId()),
            session,
            SESSION_TTL
        );
    }
}
