package com.g2rain.iam.service;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.dto.DingTalkStreamAuthorizationDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.vo.DingTalkStreamAuthorizationVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 钉钉 Stream 授权码服务
 * 功能：基于已绑定 unionId 建会话并发放 OAuth 授权码（与浏览器 OAuth 回调链路分离）
 *
 * @author Alpha
 */
@Service
@RequiredArgsConstructor
public class DingTalkStreamAuthorizationService {

    private final DingTalkIamProperties dingTalkIamProperties;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
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
    public DingTalkStreamAuthorizationVo issueStreamAuthorizationCode(DingTalkStreamAuthorizationDto req) {
        dingTalkLoginAdapterRouter.resolve(req.getBindMode());

        String unionId = req.getUnionId().trim();
        String idpApplicationCode = oauthClientIdForBindMode(req.getBindMode());
        if (Strings.isBlank(idpApplicationCode)) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "dingtalk clientId");
        }

        DingTalkPrincipal principal = new DingTalkPrincipal(
            unionId,
            null,
            Strings.isBlank(req.getCorpId()) ? null : req.getCorpId().trim(),
            "",
            req.getBindMode(),
            "{}",
            idpApplicationCode
        );
        String sessionId = authService.authenticateDingTalk(principal, false);
        SessionDto session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "session");
        }

        String userIdStr = resolveUserId(session);
        String code = authorizationService.generateAuthorizationCode(session, req.getClientId(), userIdStr, true);
        return new DingTalkStreamAuthorizationVo(code, req.getState());
    }

    /**
     * 按接入形态解析钉钉 OAuth clientId（作为 IdP 应用编码）
     *
     * @param bindMode IdP 接入形态
     * @return clientId，未配置时返回空字符串
     */
    private String oauthClientIdForBindMode(String bindMode) {
        if (IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            String v = dingTalkIamProperties.getThirdParty().getClientId();
            return v == null ? "" : v.trim();
        }
        String v = dingTalkIamProperties.getInternal().getClientId();
        return v == null ? "" : v.trim();
    }

    /**
     * 解析会话关联的用户 ID（多用户时取更新时间最新的一条）
     *
     * @param session 当前会话
     * @return 用户 ID 字符串，无用户时为 null
     */
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
