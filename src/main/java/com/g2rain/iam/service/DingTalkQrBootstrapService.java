package com.g2rain.iam.service;

import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 钉钉内嵌扫码引导服务
 * 功能：生成方式二 sns_authorize 的 goto URL，与浏览器 OAuth 共用 Redis state 与回调换票
 *
 * @author Alpha
 */
@Service
@RequiredArgsConstructor
public class DingTalkQrBootstrapService {

    private final DingTalkOAuthStateService dingTalkOAuthStateService;

    /**
     * 构建内嵌扫码引导响应
     *
     * @param bindMode    IdP 接入形态
     * @param clientId    OAuth2 客户端 ID
     * @param redirectUri OAuth2 回调地址
     * @param state       业务系统 state
     * @return 包含 goto URL 的视图对象
     */
    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String redirectUri,
                                                  String state) {
        String gotoUrl = dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, true);
        return new DingTalkQrBootstrapVo(gotoUrl);
    }
}
