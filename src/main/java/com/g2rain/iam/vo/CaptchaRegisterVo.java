package com.g2rain.iam.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 注册页数字验证码返回对象（前端展示用）。
 *
 * <p>imageBase64 为 PNG 图片的 Base64 字符串（不包含 data: 前缀）。</p>
 */
@Schema(description = "注册验证码 VO")
public record CaptchaRegisterVo(@Schema(description = "验证码 ID，提交注册时回传") String captchaId, @Schema(description = "PNG 图片 Base64 字符串（不含 data: 前缀）") String imageBase64) {
}

