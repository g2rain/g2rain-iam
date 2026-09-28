package com.g2rain.iam.controller;


import com.g2rain.basis.dto.PassportDto;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dto.AuthorizationTransactionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.service.AuthFlowCookieService;
import com.g2rain.iam.service.AuthorizationFlowService;
import com.g2rain.iam.service.AuthorizationTransactionService;
import com.g2rain.iam.service.PassportService;
import com.g2rain.iam.service.RegisterCaptchaService;
import com.g2rain.iam.utils.Constants;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 账号注册控制器。
 * <p>
 * 提供注册接口，通过 {@link PassportService} 间接调用 g2rain-basis 的
 * {@code /passport/save} 完成账号创建。注册须在授权事务（{@code tid}）内完成，
 * 成功后重定向回同一事务的授权续跑入口。
 * </p>
 * <p>
 * 前端表单：{@code register.html} 使用 {@code /auth/passport_register} 作为提交地址。
 * </p>
 *
 * @author jagger
 * @since 2026/03/14
 */
@Controller
@AllArgsConstructor
@RequestMapping("/auth")
public class PassportController {

    /**
     * Passport 注册服务，转发至 Basis 完成账号创建。
     */
    private final PassportService passportService;

    /**
     * 注册验证码与限流校验。
     */
    private final RegisterCaptchaService registerCaptchaService;

    /**
     * 授权事务 Redis 存储与状态迁移。
     */
    private final AuthorizationTransactionService transactionService;

    /**
     * 授权流程 Cookie，用于绑定 {@code tid} 与当前浏览器。
     */
    private final AuthFlowCookieService authFlowCookieService;

    /**
     * 基于 tid 的授权流程编排（渲染注册页等）。
     */
    private final AuthorizationFlowService authorizationFlowService;

    /**
     * 账号注册接口。
     * <p>
     * 从注册页面接收 {@link PassportDto}，校验验证码与限流后转发至 Basis；
     * 成功则重定向至 {@code /auth/authorize?tid=...} 继续同一授权事务。
     * </p>
     *
     * @param passportDto 注册账号所需信息（用户名、密码、真实姓名等）
     * @param captchaId   验证码 ID
     * @param captchaCode 验证码内容
     * @param tid         授权事务 ID
     * @param request     当前 HTTP 请求（限流与验证码校验）
     * @return 成功时重定向至授权续跑；失败时回到注册页并回显错误
     */
    @PostMapping("/passport_register")
    public ModelAndView register(@Valid @ModelAttribute PassportDto passportDto,
                                 @RequestParam(name = "captchaId") String captchaId,
                                 @RequestParam(name = "captchaCode") String captchaCode,
                                 @RequestParam(name = Constants.TID, required = false) String tid,
                                 HttpServletRequest request) {
        if (Strings.isBlank(tid)) {
            return authorizationFlowService.renderFlowError(
                null, IamErrorCode.AUTH_TRANSACTION_INVALID.getMessage());
        }

        String rlError = registerCaptchaService.checkRegisterRateLimit(request);
        if (rlError != null) {
            return registerError(request, tid, passportDto, rlError, null);
        }

        String captchaError = registerCaptchaService.validateRegisterCaptcha(request, captchaId, captchaCode);
        if (captchaError != null) {
            return registerError(request, tid, passportDto, captchaError, null);
        }

        Result<?> result = passportService.register(passportDto);
        if (!result.isSuccess()) {
            return registerError(request, tid, passportDto, result.getErrorMessage(), result);
        }

        String loginUrl = UriComponentsBuilder.fromPath("/auth/authorize")
            .queryParam(Constants.TID, tid.trim())
            .build()
            .toUriString();
        return new ModelAndView(Constants.REDIRECT + loginUrl);
    }

    /**
     * 注册失败时回到同一授权事务的注册页并回显错误。
     *
     * @param request     当前 HTTP 请求
     * @param tid         授权事务 ID
     * @param passportDto 已填写的注册表单
     * @param error       错误信息
     * @param result      Basis 返回结果（可选，用于页面展示）
     * @return 注册页或流程错误页
     */
    private ModelAndView registerError(
        HttpServletRequest request,
        String tid,
        PassportDto passportDto,
        String error,
        Result<?> result) {
        try {
            String flowHash = authFlowCookieService.hash(authFlowCookieService.readRaw(request));
            AuthorizationTransactionDto txn = transactionService.requireActive(tid.trim(), flowHash);
            ModelAndView mv = authorizationFlowService.renderRegister(txn);
            mv.addObject("passport", passportDto);
            mv.addObject("error", error);
            if (result != null) {
                mv.addObject("result", result);
            }
            return mv;
        } catch (BusinessException ex) {
            return authorizationFlowService.renderFlowError(null, ex.getMessage());
        }
    }
}
