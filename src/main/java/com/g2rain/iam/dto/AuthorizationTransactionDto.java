package com.g2rain.iam.dto;

import com.g2rain.iam.enums.AuthorizationMode;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 短期授权事务：冻结 OAuth 上下文，IAM 页面间仅传递 {@code tid}。
 */
@Getter
@Setter
@NoArgsConstructor
public class AuthorizationTransactionDto {

    /**
     * 授权事务 ID，页面间唯一传递的句柄。
     */
    private String tid;

    /**
     * 目标应用编码（可选）；非空时进入应用授权 consent。
     */
    private String applicationCode;

    /**
     * OAuth 客户端 ID（冻结）。
     */
    private String clientId;

    /**
     * 授权成功后的回调 URI（冻结）。
     */
    private String redirectUri;

    /**
     * 业务侧 state（已剥离匿名标记后的回调 state）。
     */
    private String state;

    /**
     * 绑定当前浏览器的 flow Cookie 哈希。
     */
    private String flowCookieHash;

    /**
     * 授权模式：用户登录或匿名。
     */
    private AuthorizationMode authorizationMode;

    /**
     * 登录成功后绑定的 IAM 会话 ID。
     */
    private String sessionId;

    /**
     * 用户确认选择的用户 ID。
     */
    private String selectedUserId;

    /**
     * 应用开通操作 ID（activate_self 幂等键）。
     */
    private String activationOperationId;

    /**
     * 应用开通租约截止时间（epoch 秒）。
     */
    private Long activationLeaseUntil;

    /**
     * 事务当前状态。
     */
    private AuthorizationTransactionStatus status;

    /**
     * 创建时间（epoch 秒）。
     */
    private Long createdAt;

    /**
     * 过期时间（epoch 秒）。
     */
    private Long expiresAt;

    /**
     * 发码成功后缓存，供终态幂等回读（可选）。
     */
    private String issuedCode;
}
