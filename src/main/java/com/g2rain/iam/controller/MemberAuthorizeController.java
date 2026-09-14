package com.g2rain.iam.controller;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.service.MemberAuthorizeService;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/member")
@Tag(name = "会员授权换票", description = "校验 memberResolveCode 并签发 SessionType=MEMBER Token")
public class MemberAuthorizeController {

    private final MemberAuthorizeService memberAuthorizeService;

    @PostMapping("/token")
    @Operation(
        summary = "会员换票",
        description = "校验 decrypt 签发的短时码，经 Gateway 编排 Member resolveOrCreate，签发或复用 MEMBER Token"
    )
    public Result<MemberAuthorizeTokenVo> token(
        @Valid @RequestBody MemberAuthorizeTokenRequest request) {
        return Result.success(memberAuthorizeService.token(request));
    }
}
