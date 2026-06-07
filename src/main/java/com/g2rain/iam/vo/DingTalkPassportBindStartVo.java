package com.g2rain.iam.vo;

/**
 * 通行证绑定钉钉扫码启动响应
 *
 * @param gotoUrl 钉钉 sns 授权 goto URL（供 DDLogin 使用）
 */
public record DingTalkPassportBindStartVo(String gotoUrl) {
}
