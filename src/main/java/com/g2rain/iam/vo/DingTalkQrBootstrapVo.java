package com.g2rain.iam.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 钉钉内嵌扫码引导返回 VO
 * 功能：封装 sns_authorize 的 goto URL，供前端 DDLogin 使用
 *
 * @author Alpha
 * @param gotoUrl 未编码的完整钉钉 sns 授权 URL（已含 opaque state 与 redirect_uri）
 */
@Schema(description = "钉钉内嵌扫码引导 VO")
public record DingTalkQrBootstrapVo(@Schema(description = "钉钉 sns 授权 goto URL（未 URL 编码）") String gotoUrl) {
}
