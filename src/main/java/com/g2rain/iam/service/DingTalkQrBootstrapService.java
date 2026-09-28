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
        return buildQrBootstrap(bindMode, clientId, redirectUri, state, null, null);
    }

    /**
     * 构建内嵌扫码引导响应（可指定 loginRole）。
     */
    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String redirectUri,
                                                  String state, String loginRole) {
        return buildQrBootstrap(bindMode, clientId, redirectUri, state, loginRole, null);
    }

    /**
     * 构建内嵌扫码引导响应（可指定 loginRole 与 applicationCode）。
     */
    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String redirectUri,
                                                  String state, String loginRole, String applicationCode) {
        return buildQrBootstrap(bindMode, clientId, redirectUri, state, loginRole, applicationCode, null);
    }

    /**
     * 构建内嵌扫码引导响应，并将授权事务 tid 写入 OAuth state。
     *
     * @param bindMode        IdP 接入形态
     * @param clientId        OAuth2 客户端 ID
     * @param redirectUri     OAuth2 回调地址
     * @param state           业务系统 state
     * @param loginRole       登录角色（可选）
     * @param applicationCode 目标应用编码（可选）
     * @param transactionId   授权事务 tid（可选）
     * @return 包含 goto URL 的视图对象
     */
    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String redirectUri,
                                                  String state, String loginRole, String applicationCode,
                                                  String transactionId) {
        String gotoUrl = dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, true, loginRole, applicationCode, transactionId);
        return new DingTalkQrBootstrapVo(gotoUrl);
    }
}
