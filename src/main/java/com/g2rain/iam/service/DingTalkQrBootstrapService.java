package com.g2rain.iam.service;

import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录页内嵌钉钉扫码（方式二）：申请 {@code sns_authorize} 的 {@code goto} URL（与浏览器方式一共用 state 写入与回调换票）。
 */
@Service
@RequiredArgsConstructor
public class DingTalkQrBootstrapService {

    private final DingTalkOAuthStateService dingTalkOAuthStateService;

    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String clientRedirectUri,
                                                  String clientState) {
        String gotoUrl = dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, clientRedirectUri, clientState, true);
        return new DingTalkQrBootstrapVo(gotoUrl);
    }
}
