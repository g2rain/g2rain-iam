package com.g2rain.iam.vo;

/**
 * 钉钉 Stream 授权码返回 VO
 * 功能：封装 OAuth 授权码及业务 state
 *
 * @author Alpha
 * @param code  OAuth 授权码
 * @param state 业务系统 state（与请求一致，可为 null）
 */
public record DingTalkStreamAuthorizationVo(String code, String state) {
}
