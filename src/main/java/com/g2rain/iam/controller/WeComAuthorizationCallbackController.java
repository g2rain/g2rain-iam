package com.g2rain.iam.controller;

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

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/wecom/third-party/authorization/callback")
public class WeComAuthorizationCallbackController {
    private final WeComCallbackCrypto callbackCrypto;
    private final WeComAuthorizationService authorizationService;

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
