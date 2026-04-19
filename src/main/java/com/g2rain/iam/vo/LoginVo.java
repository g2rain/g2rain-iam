package com.g2rain.iam.vo;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录请求参数封装对象，用于接收客户端登录请求的必要信息。
 * <p>
 * 本类通过 Jackson 注解 {@link JsonProperty} 映射前端/客户端传入的 JSON 字段名。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 接收 JSON 请求：
 * // {
 * //     "clientId": "abc123",
 * //     "redirectUri": "https://example.com/callback",
 * //     "state": "xyz"
 * // }
 *
 * LoginVo loginVo = jsonMapper.readValue(jsonString, LoginVo.class);
 * System.out.println(loginVo.getClientId());
 * System.out.println(loginVo.getRedirectUri());
 * System.out.println(loginVo.getState());
 * }</pre>
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *     <li>{@link #clientId} : 客户端 ID，前端请求中字段名为 clientId</li>
 *     <li>{@link #redirectUri} : 登录成功后的重定向 URI，字段名为 redirectUri</li>
 *     <li>{@link #state} : 防止 CSRF 的随机字符串，字段名为 state</li>
 * </ul>
 * <p>
 * Lombok 注解：
 * <ul>
 *     <li>{@link Setter} : 生成所有字段的 setter 方法</li>
 *     <li>{@link Getter} : 生成所有字段的 getter 方法</li>
 *     <li>{@link NoArgsConstructor} : 生成无参构造方法</li>
 *     <li>{@link AllArgsConstructor} : 生成全参构造方法</li>
 * </ul>
 * <p>
 * 该类常用于 OAuth2 / SSO 登录请求参数封装。
 *
 * @author alpha
 * @since 2025/10/11
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {

    /**
     * 客户端 ID，对应前端 JSON 字段 clientId
     */
    @JsonProperty(value = "clientId")
    private String clientId;

    /**
     * 登录成功后的回调重定向 URI，对应前端 JSON 字段 redirectUri
     */
    @JsonProperty(value = "redirectUri")
    private String redirectUri;

    /**
     * 防止 CSRF 攻击的状态参数，对应前端 JSON 字段 state
     */
    @JsonProperty(value = "state")
    private String state;
}
