package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通行证绑定钉钉扫码启动请求
 */
@Getter
@Setter
@NoArgsConstructor
public class DingTalkPassportBindStartDto {

    /**
     * IdP 接入形态[INTERNAL, THIRD_PARTY]；为空时使用配置 loginPageBindMode
     */
    private String bindMode;

    /**
     * 绑定完成后回跳地址；为空时使用默认结果页
     */
    private String returnUrl;
}
