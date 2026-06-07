package com.g2rain.iam.vo;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * 令牌对象（TokenVo），用于在前后端或服务间传递身份验证相关信息。
 *
 * <p>该类仅作为数据承载对象（VO），通常用于登录成功返回或 Token 校验场景。</p>
 *
 * <p>JSON 序列化说明：</p>
 * <ul>
 *     <li>{@code token} 对应 JSON 属性 "token"。</li>
 *     <li>{@code keyId} 对应 JSON 属性 "keyId"，用于标识 Token 所属密钥。</li>
 * </ul>
 *
 * <p>注：该类使用 Lombok 注解生成 getter/setter、无参构造器及全参构造器。</p>
 *
 * @author alpha
 * @since 2025/10/17
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "访问令牌 VO")
public class TokenVo {

    /**
     * 访问令牌字符串，通常用于 API 请求或身份验证。
     */
    @JsonProperty("token")
    @Schema(description = "JWT 访问令牌")
    private String token;

    /**
     * Token 对应的密钥 ID，用于区分签发或验证 Token 的密钥。
     */
    @JsonProperty("keyId")
    @Schema(description = "签发密钥 ID")
    private String keyId;
}
