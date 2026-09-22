package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WeComOAuthStateDto {
    private String bindMode;
    private String clientId;
    private String redirectUri;
    private String state;
    /** 开放平台目标应用编码 */
    private String applicationCode;
    /** 登录意图：USER 或 ADMIN */
    private String loginRole;
}
