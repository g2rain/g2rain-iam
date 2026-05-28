package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.DingTalkPassportBindStartDto;
import com.g2rain.iam.service.DingTalkPassportBindService;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.vo.DingTalkPassportBindStartVo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * 已登录通行证绑定钉钉
 * 路径前缀: /auth/dingtalk/bind/passport
 */
@Slf4j
@Controller
@AllArgsConstructor
@RequestMapping("/auth/dingtalk/bind/passport")
public class DingTalkPassportBindController {

    private final DingTalkPassportBindService dingTalkPassportBindService;

    /**
     * 启动扫码绑定，返回 DDLogin 所需 gotoUrl
     */
    @PostMapping("/start")
    @ResponseBody
    public Result<DingTalkPassportBindStartVo> start(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody(required = false) DingTalkPassportBindStartDto dto
    ) {
        return Result.success(dingTalkPassportBindService.start(authorization, dto));
    }

    /**
     * 钉钉扫码授权回调，重定向至 main-shell 绑定结果页
     */
    @GetMapping("/callback")
    public ModelAndView callback(
        @RequestParam(name = "code", required = false) String code,
        @RequestParam(name = "state", required = false) String opaqueState
    ) {
        try {
            if (code == null || code.isBlank() || opaqueState == null || opaqueState.isBlank()) {
                String url = dingTalkPassportBindService.bindErrorRedirectUrl(
                    opaqueState, "缺少授权参数，请重新扫码绑定"
                );
                return new ModelAndView(Constants.REDIRECT + url);
            }
            String redirectUrl = dingTalkPassportBindService.finishBindAndBuildRedirectUrl(code, opaqueState);
            return new ModelAndView(Constants.REDIRECT + redirectUrl);
        } catch (Exception e) {
            log.error("passport bind callback failed stateLen={} message={}",
                opaqueState == null ? 0 : opaqueState.length(), e.getMessage(), e);
            String url = dingTalkPassportBindService.bindErrorRedirectUrl(
                opaqueState, "绑定失败，请返回账号页重新发起"
            );
            return new ModelAndView(Constants.REDIRECT + url);
        }
    }
}
