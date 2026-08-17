package com.g2rain.iam.service;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.dto.WeComStreamAuthorizationDto;
import com.g2rain.iam.vo.WeComStreamAuthorizationVo;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import com.g2rain.iam.wecom.WeComPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 企业微信 Stream 授权码服务
 * 功能：基于已绑定 corpId + userId 建会话并发放 OAuth 授权码（与浏览器 OAuth 回调链路分离）
 */
@Service
@RequiredArgsConstructor
public class WeComStreamAuthorizationService {

    private final WeComIamProperties weComIamProperties;
    private final WeComLoginAdapterRouter weComLoginAdapterRouter;
    private final AuthService authService;
    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final UserService userService;

    /**
     * 为 Stream / 消息应用场景发放 OAuth 授权码
     *
     * @param req Stream 发码请求 DTO
     * @return 授权码及 state
     */
    public WeComStreamAuthorizationVo issueStreamAuthorizationCode(WeComStreamAuthorizationDto req) {
        weComLoginAdapterRouter.resolve(req.getBindMode());

        String corpId = req.getCorpId().trim();
        String userId = req.getUserId().trim();
        String idpApplicationCode = idpApplicationCodeForBindMode(req.getBindMode());
        if (Strings.isBlank(idpApplicationCode)) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "wecom idpApplicationCode");
        }

        WeComPrincipal principal = new WeComPrincipal(
            corpId,
            userId,
            "",
            "",
            req.getBindMode(),
            idpApplicationCode,
            "",
            "{}"
        );
        String sessionId = authService.authenticateIdp(principal.toIdpPrincipal(), false);
        SessionDto session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "session");
        }

        String userIdStr = resolveUserId(session);
        String code = authorizationService.generateAuthorizationCode(session, req.getClientId(), userIdStr, true);
        return new WeComStreamAuthorizationVo(code, req.getState());
    }

    private String idpApplicationCodeForBindMode(String bindMode) {
        if (IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            String v = weComIamProperties.getThirdParty().getSuiteId();
            return v == null ? "" : v.trim();
        }
        String v = weComIamProperties.getInternal().getAgentId();
        return v == null ? "" : v.trim();
    }

    private String resolveUserId(SessionDto session) {
        List<UserVo> users = userService.listUserVos(session);
        if (Collections.isEmpty(users)) {
            return null;
        }
        if (users.size() == 1) {
            return String.valueOf(users.getFirst().getId());
        }
        UserVo latest = users.stream().max(
            Comparator.<UserVo, String>comparing(u -> Strings.isBlank(u.getUpdateTime()) ? "" : u.getUpdateTime())
                .thenComparing(u -> u.getId() != null ? u.getId() : 0L)
        ).orElse(users.getFirst());
        return String.valueOf(latest.getId());
    }
}
