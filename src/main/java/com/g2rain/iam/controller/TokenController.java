package com.g2rain.iam.controller;


import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.GenerateTokenDto;
import com.g2rain.iam.service.TokenService;
import com.g2rain.iam.vo.TokenVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌控制器，负责处理令牌生成的请求。
 * <p>
 * 该控制器处理客户端的令牌请求，通过传入的参数生成 JWT 令牌，并将其返回给客户端。该操作通常用于 OAuth2 或 OpenID Connect 中的令牌发放。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 通过 POST 请求生成令牌
 * /auth/token?grant_type=authorization_code&code=x
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@RestController
@AllArgsConstructor
@RequestMapping(value = "/auth")
@Tag(name = "Token", description = "Token 相关接口")
public class TokenController {

    /**
     * 令牌服务，提供生成和验证令牌的业务逻辑。
     */
    private TokenService tokenService;

    /**
     * 令牌生成接口，接收客户端的请求并返回生成的 JWT 令牌。
     * <p>
     * 该方法接收客户端传递的令牌生成请求参数，通过 `TokenService` 生成 JWT 令牌，并返回给客户端。
     * </p>
     *
     * @param clientDPoP       客户端级 DPoP
     * @param applicationDPoP  应用级 DPoP
     * @param authorization    token
     * @param generateTokenDto 令牌生成请求数据传输对象
     * @return {@link Result<String>} 包含生成的 JWT 令牌
     */
    @PostMapping(value = "/token")
    @Operation(summary = "获取 Token", description = "根据授权码或交换令牌模式生成 JWT 访问令牌")
    public Result<TokenVo> token(
        @Parameter(description = "客户端级 DPoP 证明", required = true) @RequestHeader(name = "DPoP") String clientDPoP,
        @Parameter(description = "应用级 DPoP 证明") @RequestHeader(name = "application-DPoP", required = false) String applicationDPoP,
        @Parameter(description = "Bearer 令牌（如 refresh_token 换票场景）") @RequestHeader(name = "Authorization", required = false) String authorization,
        @Parameter(description = "令牌生成请求参数（application/x-www-form-urlencoded）", required = true) @Valid @ModelAttribute GenerateTokenDto generateTokenDto) {
        return Result.success(tokenService.generateToken(clientDPoP, applicationDPoP, authorization, generateTokenDto));
    }
}
