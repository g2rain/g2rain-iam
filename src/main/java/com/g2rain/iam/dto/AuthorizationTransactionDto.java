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

    private String tid;

    private String applicationCode;

    private String clientId;

    private String redirectUri;

    private String state;

    private String flowCookieHash;

    private AuthorizationMode authorizationMode;

    private String sessionId;

    private String selectedUserId;

    private String activationOperationId;

    private Long activationLeaseUntil;

    private AuthorizationTransactionStatus status;

    private Long createdAt;

    private Long expiresAt;

    /** 发码成功后缓存，供终态幂等回读（可选）。 */
    private String issuedCode;
}
