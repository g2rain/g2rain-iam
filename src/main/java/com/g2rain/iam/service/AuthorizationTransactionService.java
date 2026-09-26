package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.AuthorizationMode;
import com.g2rain.iam.enums.AuthorizationTransactionStatus;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.AuthorizationState;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.HexFormat;

/**
 * 授权事务 Redis 存储与状态迁移。
 */
@Service
@RequiredArgsConstructor
public class AuthorizationTransactionService {

    private final GenericRedisHelper genericRedisHelper;
    private final IamAccessProperties iamAccessProperties;

    public Duration ttl() {
        int seconds = Math.max(60, iamAccessProperties.getAuthorizationTransaction().getTtlSeconds());
        return Duration.ofSeconds(seconds);
    }

    public AuthorizationTransactionDto get(String tid) {
        if (Strings.isBlank(tid)) {
            return null;
        }
        AuthorizationTransactionDto dto = genericRedisHelper.get(
            RedisKeyRule.AUTHORIZATION_TRANSACTION.format(tid.trim()),
            AuthorizationTransactionDto.class
        );
        if (dto == null) {
            return null;
        }
        if (dto.getExpiresAt() != null && Instant.now().getEpochSecond() > dto.getExpiresAt()) {
            return null;
        }
        return dto;
    }

    public AuthorizationTransactionDto requireActive(String tid, String flowCookieHash) {
        AuthorizationTransactionDto dto = get(tid);
        if (dto == null
            || Strings.isBlank(dto.getFlowCookieHash())
            || !Objects.equals(dto.getFlowCookieHash(), flowCookieHash)
            || dto.getStatus() == null
            || dto.getStatus().isTerminal()) {
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
        }
        return dto;
    }

    public AuthorizationTransactionDto requireReadable(String tid, String flowCookieHash) {
        AuthorizationTransactionDto dto = get(tid);
        if (dto == null
            || Strings.isBlank(dto.getFlowCookieHash())
            || !Objects.equals(dto.getFlowCookieHash(), flowCookieHash)) {
            throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
        }
        return dto;
    }

    /**
     * 创建或复用未完成事务。flow Cookie 不匹配已有 dedup 记录时拒绝。
     */
    public AuthorizationTransactionDto createOrReuse(
        String clientId,
        String redirectUri,
        String state,
        String applicationCode,
        String flowCookieHash) {
        AuthorizationMode mode = AuthorizationState.isAnonymous(state)
            ? AuthorizationMode.ANONYMOUS
            : AuthorizationMode.USER;
        String businessState = AuthorizationState.resolveCallbackState(state);
        String appKey = Strings.isBlank(applicationCode) ? "_" : applicationCode.trim();
        String stateKey = Strings.isBlank(businessState) ? "_" : sha256(businessState);
        String dedupKey = RedisKeyRule.AUTHORIZATION_DEDUP.format(
            mode.name(), appKey, clientId.trim(), stateKey);

        String existingTid = genericRedisHelper.get(dedupKey, String.class);
        if (Strings.isNotBlank(existingTid)) {
            AuthorizationTransactionDto existing = get(existingTid);
            if (existing != null && !existing.getStatus().isTerminal()) {
                if (!Objects.equals(existing.getFlowCookieHash(), flowCookieHash)) {
                    throw new BusinessException(IamErrorCode.AUTH_TRANSACTION_INVALID);
                }
                return existing;
            }
        }

        Instant now = Instant.now();
        Duration ttl = ttl();
        AuthorizationTransactionDto dto = new AuthorizationTransactionDto();
        dto.setTid(IamUtils.generateAuthorizationCode());
        dto.setClientId(clientId.trim());
        dto.setRedirectUri(redirectUri.trim());
        dto.setState(businessState);
        dto.setApplicationCode(Strings.isBlank(applicationCode) ? null : applicationCode.trim());
        dto.setFlowCookieHash(flowCookieHash);
        dto.setAuthorizationMode(mode);
        dto.setStatus(AuthorizationTransactionStatus.CREATED);
        dto.setCreatedAt(now.getEpochSecond());
        dto.setExpiresAt(now.plus(ttl).getEpochSecond());
        persist(dto, ttl);
        genericRedisHelper.set(dedupKey, dto.getTid(), ttl);
        addToFlowIndex(flowCookieHash, dto.getTid(), ttl);
        return dto;
    }

    public boolean compareAndUpdate(
        String tid,
        AuthorizationTransactionStatus expected,
        AuthorizationTransactionStatus next,
        Consumer<AuthorizationTransactionDto> mutator) {
        AuthorizationTransactionDto dto = get(tid);
        if (dto == null || dto.getStatus() != expected) {
            return false;
        }
        if (mutator != null) {
            mutator.accept(dto);
        }
        dto.setStatus(next);
        Duration remaining = remainingTtl(dto);
        persist(dto, remaining);
        if (next.isTerminal()) {
            removeFromFlowIndex(dto.getFlowCookieHash(), tid);
        }
        return true;
    }

    public void forceCancel(AuthorizationTransactionDto dto) {
        if (dto == null || dto.getStatus() == null || dto.getStatus().isTerminal()) {
            return;
        }
        dto.setStatus(AuthorizationTransactionStatus.CANCELLED);
        persist(dto, remainingTtl(dto));
        removeFromFlowIndex(dto.getFlowCookieHash(), dto.getTid());
    }

    public List<AuthorizationTransactionDto> listActiveByFlowHash(String flowCookieHash) {
        List<AuthorizationTransactionDto> result = new ArrayList<>();
        if (Strings.isBlank(flowCookieHash)) {
            return result;
        }
        @SuppressWarnings("unchecked")
        Set<String> tids = genericRedisHelper.get(
            RedisKeyRule.AUTHORIZATION_FLOW_INDEX.format(flowCookieHash),
            Set.class
        );
        if (tids == null || tids.isEmpty()) {
            return result;
        }
        for (String tid : tids) {
            AuthorizationTransactionDto dto = get(tid);
            if (dto != null && dto.getStatus() != null && !dto.getStatus().isTerminal()) {
                result.add(dto);
            }
        }
        return result;
    }

    public void cancelAllByFlowHash(String flowCookieHash) {
        for (AuthorizationTransactionDto dto : listActiveByFlowHash(flowCookieHash)) {
            forceCancel(dto);
        }
        if (Strings.isNotBlank(flowCookieHash)) {
            genericRedisHelper.delete(RedisKeyRule.AUTHORIZATION_FLOW_INDEX.format(flowCookieHash));
        }
    }

    private void persist(AuthorizationTransactionDto dto, Duration ttl) {
        genericRedisHelper.set(
            RedisKeyRule.AUTHORIZATION_TRANSACTION.format(dto.getTid()),
            dto,
            ttl
        );
    }

    private Duration remainingTtl(AuthorizationTransactionDto dto) {
        if (dto.getExpiresAt() == null) {
            return ttl();
        }
        long remaining = dto.getExpiresAt() - Instant.now().getEpochSecond();
        if (remaining < 1) {
            return Duration.ofSeconds(1);
        }
        return Duration.ofSeconds(remaining);
    }

    @SuppressWarnings("unchecked")
    private void addToFlowIndex(String flowCookieHash, String tid, Duration ttl) {
        String key = RedisKeyRule.AUTHORIZATION_FLOW_INDEX.format(flowCookieHash);
        Set<String> tids = genericRedisHelper.get(key, Set.class);
        if (tids == null) {
            tids = new HashSet<>();
        } else {
            tids = new HashSet<>(tids);
        }
        tids.add(tid);
        genericRedisHelper.set(key, tids, ttl);
    }

    @SuppressWarnings("unchecked")
    private void removeFromFlowIndex(String flowCookieHash, String tid) {
        if (Strings.isBlank(flowCookieHash) || Strings.isBlank(tid)) {
            return;
        }
        String key = RedisKeyRule.AUTHORIZATION_FLOW_INDEX.format(flowCookieHash);
        Set<String> tids = genericRedisHelper.get(key, Set.class);
        if (tids == null || tids.isEmpty()) {
            return;
        }
        Set<String> next = new HashSet<>(tids);
        next.remove(tid);
        if (next.isEmpty()) {
            genericRedisHelper.delete(key);
        } else {
            genericRedisHelper.set(key, next, ttl());
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
