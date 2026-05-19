package com.g2rain.iam.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话信息 DTO 类，存储与用户会话相关的数据。
 * <p>
 * 该类用于存储用户的会话信息，包括会话 ID、护照 ID 和用户名。通常用于管理和验证用户会话。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建一个新的会话 DTO 并设置属性
 * SessionDto session = new SessionDto();
 * session.setSessionId("session123");
 * session.setPassportId("passport123");
 * session.setName("John Doe");
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/13
 */
@Setter
@Getter
@NoArgsConstructor
public class SessionDto {

    /**
     * 会话 ID，标识当前用户会话的唯一 ID。
     * <p>
     * 该字段存储用户的会话 ID，通常用于会话管理和身份验证。
     * </p>
     */
    private String sessionId;

    /**
     * 护照 ID，标识用户的唯一身份。
     * <p>
     * 该字段存储与用户关联的护照 ID，通常用于跨系统的身份验证。
     * </p>
     */
    private String passportId;

    /**
     * 用户名，存储用户的名字。
     * <p>
     * 该字段存储用户的姓名，通常用于显示在用户界面上。
     * </p>
     */
    private String name;

    /**
     * 身份源类型[DINGTALK:钉钉]；密码登录时为空
     */
    private String idpType;

    /**
     * IdP 稳定主体（如钉钉 unionId）；密码登录时为空
     */
    private String idpSubject;

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    private String idpBindMode;

    /**
     * IdP 应用编码（如钉钉 OAuth clientId）
     */
    private String idpApplicationCode;
}
