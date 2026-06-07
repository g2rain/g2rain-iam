package com.g2rain.iam.controller;

import com.g2rain.iam.service.RegisterCaptchaService;
import com.g2rain.iam.vo.CaptchaRegisterVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册验证码独立接口（图片 + captchaId）。
 */
@RestController
@AllArgsConstructor
@RequestMapping("/auth")
@Tag(name = "验证码", description = "验证码相关接口")
public class CaptchaController {

    private final RegisterCaptchaService registerCaptchaService;

    /**
     * 获取注册验证码图片。
     */
    @GetMapping("/captcha/register")
    @Operation(summary = "获取验证码", hidden = true, description = "获取注册页数字验证码图片")
    public CaptchaRegisterVo captchaRegister(@Parameter(description = "HTTP 请求（用于 IP 限流等，无显式入参）", hidden = true) HttpServletRequest request) {
        return registerCaptchaService.generateRegisterCaptcha(request);
    }
}

