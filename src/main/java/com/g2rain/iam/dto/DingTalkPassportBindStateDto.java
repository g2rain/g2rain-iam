package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通行证绑定钉钉扫码 Redis state 载荷
 */
@Getter
@Setter
@NoArgsConstructor
public class DingTalkPassportBindStateDto {

    private Long passportId;

    private Long organId;

    private String bindMode;

    private String returnUrl;

    /** 发起绑定时 SessionType 枚举名（如 USER、PASSPORT） */
    private String sessionType;

    /** 发起绑定时是否为机构管理员（user.admin） */
    private Boolean adminUser;
}
