package com.g2rain.iam.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 为 IAM Thymeleaf 页面注入全局 UI 模型属性。
 */
@ControllerAdvice
@RequiredArgsConstructor
public class IamUiModelAdvice {

    private final IamAccessProperties iamAccessProperties;

    @ModelAttribute("brandName")
    public String brandName() {
        return iamAccessProperties.resolvedBrandName();
    }
}
