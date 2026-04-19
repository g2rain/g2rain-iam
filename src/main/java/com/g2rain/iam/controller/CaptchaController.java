package com.g2rain.iam.controller;

import com.g2rain.iam.service.RegisterCaptchaService;
import com.g2rain.iam.vo.CaptchaRegisterVo;
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
public class CaptchaController {

    private final RegisterCaptchaService registerCaptchaService;

    /**
     * 获取注册验证码图片。
     */
    @GetMapping("/captcha/register")
    public CaptchaRegisterVo captchaRegister(HttpServletRequest request) {
        return registerCaptchaService.generateRegisterCaptcha(request);
    }
}

