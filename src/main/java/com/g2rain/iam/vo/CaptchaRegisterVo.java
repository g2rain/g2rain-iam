package com.g2rain.iam.vo;

/**
 * 注册页数字验证码返回对象（前端展示用）。
 *
 * <p>imageBase64 为 PNG 图片的 Base64 字符串（不包含 data: 前缀）。</p>
 */
public record CaptchaRegisterVo(
    String captchaId,
    String imageBase64
) {
}

