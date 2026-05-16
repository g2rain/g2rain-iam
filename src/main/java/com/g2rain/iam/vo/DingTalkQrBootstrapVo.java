package com.g2rain.iam.vo;

/**
 * 内嵌扫码（方式二）：前端将 {@link #gotoUrl()} 整体 {@code encodeURIComponent} 后传入钉钉 {@code DDLogin} 的 {@code goto}；
 * {@code gotoUrl} 为 {@code oapi.dingtalk.com/connect/oauth2/sns_authorize?...}；扫码成功后拼接 {@code loginTmpCode} 再整页跳转，
 * 由钉钉重定向至 IAM {@code /auth/dingtalk/callback}。
 *
 * @param gotoUrl 未编码的完整钉钉 sns 授权 URL（已含 opaque {@code state} 与 {@code redirect_uri}）
 */
public record DingTalkQrBootstrapVo(String gotoUrl) {
}
