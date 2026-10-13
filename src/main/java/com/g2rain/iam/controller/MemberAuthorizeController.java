package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.service.MemberAuthorizeService;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员授权换票控制器。
 * <p>
 * 校验客服 decrypt 签发的 {@code memberResolveCode}，经 Member {@code resolveOrCreate}
 * 后签发或复用 {@code SessionType=MEMBER} Token；DPoP 协议与 {@code /auth/token} 对齐。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/member")
@Tag(name = "会员授权换票", description = "校验 memberResolveCode 并签发 SessionType=MEMBER Token")
public class MemberAuthorizeController {

    /**
     * 会员换票业务服务。
     */
    private final MemberAuthorizeService memberAuthorizeService;

    /**
     * 会员换票接口。
     * <p>
     * 校验短时可复用码（同回调可多次换票），完成 Client/Application DPoP 校验后签发 MEMBER Token。
     * </p>
     *
     * @param clientDPoP      客户端级 DPoP 证明
     * @param applicationDPoP 应用级 DPoP 证明
     * @param request         含 memberResolveCode 与外部用户标识的换票请求
     * @return 会员访问令牌视图
     */
    @PostMapping("/token")
    @Operation(
        summary = "会员换票",
        description = "校验 decrypt 签发的短时可复用码（同回调可多次换票），经 Member resolveOrCreate，并按与 /auth/token 相同的 Client/Application DPoP 协议签发或复用 MEMBER Token"
    )
    public Result<MemberAuthorizeTokenVo> token(
        @Parameter(description = "客户端级 DPoP 证明", required = true) @RequestHeader(name = "DPoP") String clientDPoP,
        @Parameter(description = "应用级 DPoP 证明", required = true) @RequestHeader(name = "application-DPoP") String applicationDPoP,
        @Valid @RequestBody MemberAuthorizeTokenRequest request) {
        return Result.success(memberAuthorizeService.token(clientDPoP, applicationDPoP, request));
    }
}
