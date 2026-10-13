package com.g2rain.iam.service;

import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUrlUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.servlet.ModelAndView;

/**
 * 通用页面错误与平台跳转；授权编排已迁至 {@link AuthorizationFlowService}。
 */
@Service
public class ModelAndViewService {

    @Resource
    private IamAccessProperties iamAccessProperties;

    /**
     * 当客户端 ID 或重定向 URI 为空时，返回错误页面；参数齐全时返回 {@code null}。
     */
    public ModelAndView redirectError(String clientId, String redirectUri, String state) {
        if (Strings.isBlank(clientId) || Strings.isBlank(redirectUri)) {
            ModelAndView modelAndView = new ModelAndView("error");
            ModelMap model = modelAndView.getModelMap();
            String errorMessage = null;
            if (Strings.isBlank(clientId)) {
                errorMessage = SystemErrorCode.PARAM_REQUIRED.getMessage("clientId");
            }
            if (Strings.isBlank(redirectUri)) {
                if (Strings.isBlank(clientId)) {
                    errorMessage += "，";
                }
                errorMessage += SystemErrorCode.PARAM_REQUIRED.getMessage("redirectUri");
            }
            model.addAttribute("error", errorMessage);
            model.addAttribute("redirectUri", Strings.isBlank(redirectUri) ? "" : redirectUri);
            model.addAttribute("state", state != null ? state : "");
            return modelAndView;
        }
        return null;
    }

    /**
     * OAuth 流程错误页（clientId / redirectUri 已校验非空）。
     */
    public ModelAndView redirectOAuthError(String clientId, String redirectUri, String state, String errorMessage) {
        ModelAndView modelAndView = new ModelAndView("error");
        ModelMap model = modelAndView.getModelMap();
        model.addAttribute("error", errorMessage);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("state", state != null ? state : "");
        return modelAndView;
    }

    /**
     * 重定向到业务平台控制台首页。
     */
    public ModelAndView redirectPlatformMainHome() {
        String url = IamUrlUtils.joinAbsoluteUrl(
            iamAccessProperties.resolvedPlatformBaseUrl(), "/main", "/home");
        return new ModelAndView(Constants.REDIRECT + url);
    }
}
