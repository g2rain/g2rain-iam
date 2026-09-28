package com.g2rain.iam.controller.wecom;

import com.g2rain.iam.service.WeComAuthorizationService;
import com.g2rain.iam.wecom.WeComCallbackCrypto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业微信第三方应用授权事件回调控制器。
 * <p>
 * 处理 URL 验证（echostr 解密回显）、安装授权完成（auth_code 激活）以及授权变更/取消等推送事件。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/wecom/third-party/authorization/callback")
public class WeComAuthorizationCallbackController {

    /**
     * 企业微信回调加解密工具。
     */
    private final WeComCallbackCrypto callbackCrypto;

    /**
     * 第三方应用授权开通与撤销服务。
     */
    private final WeComAuthorizationService authorizationService;

    /**
     * GET 回调：URL 验证回显，或携带 {@code auth_code} 完成安装授权激活。
     *
     * @param signature 消息签名
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param echostr   URL 验证加密串（验证阶段）
     * @param authCode  安装授权码（激活阶段）
     * @return 明文 echostr 或 {@code success}
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "企业微信授权回调验证或安装授权完成", hidden = true)
    public ResponseEntity<String> verifyOrActivate(
        @RequestParam(name = "msg_signature", required = false) String signature,
        @RequestParam(required = false) String timestamp,
        @RequestParam(required = false) String nonce,
        @RequestParam(required = false) String echostr,
        @RequestParam(name = "auth_code", required = false) String authCode) {
        if (authCode != null && !authCode.isBlank()) {
            authorizationService.activate(authCode);
            return ResponseEntity.ok("success");
        }
        return ResponseEntity.ok(
            callbackCrypto.decryptEcho(signature, timestamp, nonce, echostr));
    }

    /**
     * POST 回调：接收授权变更等加密事件推送。
     *
     * @param signature 消息签名
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param body      加密 XML 请求体
     * @return {@code success}
     */
    @PostMapping(
        consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    @Operation(summary = "企业微信第三方应用授权事件回调", hidden = true)
    public ResponseEntity<String> receive(
        @RequestParam(name = "msg_signature") String signature,
        @RequestParam String timestamp,
        @RequestParam String nonce,
        @RequestBody String body) {
        authorizationService.handleDecryptedEvent(
            callbackCrypto.decryptMessage(signature, timestamp, nonce, body));
        return ResponseEntity.ok("success");
    }
}
