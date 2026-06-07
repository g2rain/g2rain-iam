package com.g2rain.iam.service;


import com.g2rain.basis.dto.LoginDto;
import com.g2rain.basis.vo.PassportVo;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.LoginClient;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.idp.IdpPrincipal;
import com.g2rain.iam.service.idp.IdpAuthServiceRouter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 认证编排：凭证登录与 IdP 登录入口，会话读写委托 {@link SessionService}。
 */
@Service
public class AuthService {

    @Resource
    private LoginClient loginClient;

    @Resource
    private SessionService sessionService;

    @Resource
    private IdpAuthServiceRouter idpAuthServiceRouter;

    /**
     * 账号密码登录并创建会话
     */
    public String authenticate(String username, String password) {
        Result<PassportVo> result = loginClient.login(new LoginDto(username, password));
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        PassportVo passport = result.getData();
        return sessionService.createPassportSession(
            Objects.toString(passport.getId(), null),
            passport.getRealName()
        );
    }

    /**
     * IdP 登录并创建会话
     */
    public String authenticateIdp(IdpPrincipal principal, boolean autoProvisionMissingPassport) {
        String passportId = idpAuthServiceRouter.resolvePassportId(principal, autoProvisionMissingPassport);
        return sessionService.createIdpSession(passportId, principal);
    }

    /**
     * 钉钉登录并创建会话（浏览器 OAuth：无绑定时自动注册 passport）
     */
    public String authenticateDingTalk(DingTalkPrincipal principal) {
        return authenticateDingTalk(principal, true);
    }

    /**
     * 钉钉登录并创建会话
     */
    public String authenticateDingTalk(DingTalkPrincipal principal, boolean autoProvisionMissingPassport) {
        return authenticateIdp(principal.toIdpPrincipal(), autoProvisionMissingPassport);
    }

    public boolean isSessionExpired(String sessionId) {
        return sessionService.isSessionExpired(sessionId);
    }

    public SessionDto getSession(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    public void logout(String sessionId) {
        sessionService.logout(sessionId);
    }
}
