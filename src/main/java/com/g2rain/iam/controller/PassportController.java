package com.g2rain.iam.controller;


import com.g2rain.basis.dto.PassportDto;
import com.g2rain.common.model.Result;
import com.g2rain.iam.service.ModelAndViewService;
import com.g2rain.iam.service.PassportService;
import com.g2rain.iam.service.RegisterCaptchaService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 账号注册控制器。
 *
 * <p>该控制器提供注册接口，通过 {@link PassportService} 间接调用 g2rain-basis 的
 * {@code /passport/save} 接口完成账号创建。</p>
 *
 * <p>前端表单：{@code register.html} 使用 {@code /passport/register} 作为提交地址。</p>
 *
 * @author jagger
 * @since 2026/03/14
 */
@Controller
@AllArgsConstructor
@RequestMapping("/auth")
public class PassportController {

    private final PassportService passportService;
    private final ModelAndViewService modelAndViewService;
    private final RegisterCaptchaService registerCaptchaService;

    /**
     * 账号注册接口。
     *
     * <p>从注册页面接收 {@link PassportDto} 参数，并转发到 g2rain-basis 的
     * {@code /passport/save} 接口。</p>
     *
     * @param passportDto 注册账号所需的信息，包含用户名、密码、真实姓名等字段
     * @param clientId    客户端 ID（从表单隐藏字段获取）
     * @param redirectUri 登录后重定向的 URI（从表单隐藏字段获取）
     * @param state       请求的状态参数（从表单隐藏字段获取）
     * @return 注册成功后跳转到登录页视图（带参数），失败则回到注册页并显示错误信息
     */
    @PostMapping("/passport_register")
    public ModelAndView register(@Valid @ModelAttribute PassportDto passportDto,
                                 @RequestParam(name = "captchaId") String captchaId,
                                 @RequestParam(name = "captchaCode") String captchaCode,
                                 @RequestParam(name = "clientId", required = false) String clientId,
                                 @RequestParam(name = "redirectUri", required = false) String redirectUri,
                                 @RequestParam(name = "state", required = false) String state,
                                 HttpServletRequest request) {
        // 1) 注册限流（同 IP）
        String rlError = registerCaptchaService.checkRegisterRateLimit(request);
        if (rlError != null) {
            ModelAndView mv = new ModelAndView("register");
            mv.addObject("passport", passportDto);
            mv.addObject("clientId", clientId);
            mv.addObject("redirectUri", redirectUri);
            mv.addObject("state", state);
            mv.addObject("error", rlError);
            return mv;
        }

        // 2) 校验验证码
        String captchaError = registerCaptchaService.validateRegisterCaptcha(request, captchaId, captchaCode);
        if (captchaError != null) {
            ModelAndView mv = new ModelAndView("register");
            mv.addObject("passport", passportDto);
            mv.addObject("clientId", clientId);
            mv.addObject("redirectUri", redirectUri);
            mv.addObject("state", state);
            mv.addObject("error", captchaError);
            return mv;
        }

        Result<?> result = passportService.register(passportDto);

        if (!result.isSuccess()) {
            ModelAndView mv = new ModelAndView("register");
            mv.addObject("passport", passportDto);
            mv.addObject("clientId", clientId);
            mv.addObject("redirectUri", redirectUri);
            mv.addObject("state", state);
            mv.addObject("result", result);
            mv.addObject("error", result.getErrorMessage());
            return mv;
        }

        // 注册成功后，跳转到登录页面并带上相关参数
        return modelAndViewService.redirectLogin(clientId, redirectUri, state);
    }
}

