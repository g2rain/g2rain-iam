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
 * 账号注册：须在授权事务（tid）内完成。
 */
@Controller
@AllArgsConstructor
@RequestMapping("/auth")
public class PassportController {

    private final PassportService passportService;
    private final RegisterCaptchaService registerCaptchaService;
    private final AuthorizationTransactionService transactionService;
    private final AuthFlowCookieService authFlowCookieService;
    private final AuthorizationFlowService authorizationFlowService;

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
