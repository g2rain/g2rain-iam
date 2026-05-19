package com.g2rain.iam.vo;

/**
 * 钉钉内嵌扫码引导返回 VO
 * 功能：封装 sns_authorize 的 goto URL，供前端 DDLogin 使用
 *
 * @author Alpha
 * @param gotoUrl 未编码的完整钉钉 sns 授权 URL（已含 opaque state 与 redirect_uri）
 */
public record DingTalkQrBootstrapVo(String gotoUrl) {
}
