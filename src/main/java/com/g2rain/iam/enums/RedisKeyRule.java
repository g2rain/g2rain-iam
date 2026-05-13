package com.g2rain.iam.enums;


import com.g2rain.common.utils.Collections;
import lombok.Getter;

/**
 * Redis 密钥规则枚举类，定义了常用的 Redis 键的格式规则。
 * <p>
 * 该类定义了应用中常见的 Redis 键规则，并提供了通过格式化字符串生成实际 Redis 键的方法。
 * </p>
 * <p>
 * 示例：
 * <pre>{@code
 * // 生成 session 的 Redis 键
 * String sessionKey = RedisKeyRule.SESSION.format(sessionId);
 *
 * // 生成授权码的 Redis 键
 * String authorizationCodeKey = RedisKeyRule.AUTHORIZATION_CODE.format(code);
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/13
 */
@Getter
public enum RedisKeyRule {

    /**
     * 会话相关的 Redis 键规则，格式为 "auth:session:{sessionId}"
     */
    SESSION("auth:session:%s"),

    /**
     * 授权码相关的 Redis 键规则，格式为 "auth:authorization:{authorizationCode}"
     */
    AUTHORIZATION_CODE("auth:authorization:%s"),

    /**
     * 钉钉 OAuth 防 CSRF 的 state，格式为 "auth:dingtalk:oauth:state:{state}"
     */
    DINGTALK_OAUTH_STATE("auth:dingtalk:oauth:state:%s");

    /**
     * Redis 键的格式规则，使用占位符 %s。
     */
    private final String key;

    /**
     * 构造方法，初始化 Redis 键规则的格式。
     *
     * @param key Redis 键的格式规则
     */
    RedisKeyRule(String key) {
        this.key = key;
    }

    /**
     * 格式化 Redis 键，将占位符替换为传入的值。
     * <p>
     * 如果没有传入值，则直接返回未格式化的键规则字符串。
     * </p>
     *
     * @param values 需要替换占位符的值
     * @return 格式化后的 Redis 键
     */
    public String format(String... values) {
        if (Collections.isEmpty(values)) {
            return this.key;
        }

        return String.format(this.key,
            (Object[]) values
        );
    }
}
