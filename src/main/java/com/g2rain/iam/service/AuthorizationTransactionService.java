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
 * <p>
 * 负责 tid 创建/复用、flow Cookie 绑定校验、CAS 状态推进，以及按 flow 维度取消未完成事务。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationTransactionService {

    private final GenericRedisHelper genericRedisHelper;
    private final IamAccessProperties iamAccessProperties;

    /**
     * 授权事务 TTL（至少 60 秒）。
     *
     * @return 事务存活时长
     */
    public Duration ttl() {
        int seconds = Math.max(60, iamAccessProperties.getAuthorizationTransaction().getTtlSeconds());
        return Duration.ofSeconds(seconds);
    }

    /**
     * 按 tid 读取事务；不存在或已过期返回 {@code null}。
     *
     * @param tid 授权事务 ID
     * @return 事务 DTO，或 {@code null}
     */
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

    /**
     * 要求事务存在、flow Cookie 匹配且未终态；否则抛出业务异常。
     *
     * @param tid            授权事务 ID
     * @param flowCookieHash 当前浏览器 flow Cookie 的哈希
     * @return 活跃事务
     */
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

    /**
     * 要求事务存在且 flow Cookie 匹配（允许终态，用于幂等回读）。
     *
     * @param tid            授权事务 ID
     * @param flowCookieHash 当前浏览器 flow Cookie 的哈希
     * @return 可读事务
     */
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

    /**
     * CAS 更新事务状态：仅当当前状态等于 {@code expected} 时推进到 {@code next}。
     *
     * @param tid      授权事务 ID
     * @param expected 期望的当前状态
     * @param next     目标状态
     * @param mutator  状态推进前的字段变更（可为 null）
     * @return 是否更新成功
     */
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

    /**
     * 强制取消未终态事务（标记 {@code CANCELLED} 并从 flow 索引移除）。
     *
     * @param dto 授权事务
     */
    public void forceCancel(AuthorizationTransactionDto dto) {
        if (dto == null || dto.getStatus() == null || dto.getStatus().isTerminal()) {
            return;
        }
        dto.setStatus(AuthorizationTransactionStatus.CANCELLED);
        persist(dto, remainingTtl(dto));
        removeFromFlowIndex(dto.getFlowCookieHash(), dto.getTid());
    }

    /**
     * 列出同一 flow Cookie 下仍活跃的授权事务。
     *
     * @param flowCookieHash flow Cookie 哈希
     * @return 活跃事务列表（可能为空）
     */
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

    /**
     * 取消同一 flow Cookie 下全部未完成事务（用于本浏览器退出）。
     *
     * @param flowCookieHash flow Cookie 哈希
     */
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
