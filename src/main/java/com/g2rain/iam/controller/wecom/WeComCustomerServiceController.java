package com.g2rain.iam.controller.wecom;

import com.g2rain.common.model.Result;
import com.g2rain.iam.dto.WeComCustomerServiceDecryptRequest;
import com.g2rain.iam.service.WeComCustomerServiceDecryptService;
import com.g2rain.iam.vo.WeComCustomerServiceDecryptVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业微信客服回调解密控制器。
 * <p>
 * 供客服/内部应用调用：验签解密、校验 ACTIVE 授权与 organ 映射，签发 {@code memberResolveCode}。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/wecom/customer_service")
@Tag(name = "企业微信客服回调", description = "客服模块回调解密与定租户")
public class WeComCustomerServiceController {

    /**
     * 客服回调解密与 memberResolveCode 签发服务。
     */
    private final WeComCustomerServiceDecryptService decryptService;

    /**
     * 验签解密客服回调并签发会员解析码。
     *
     * @param request 加密回调载荷与验签参数
     * @return 含 organ 与 memberResolveCode 的解密结果
     */
    @PostMapping("/decrypt")
    @Operation(
        summary = "验签解密客服回调",
        description = "客服/内部应用调用：验签解密、校验 ACTIVE 授权与 organ 映射，签发 memberResolveCode"
    )
    public Result<WeComCustomerServiceDecryptVo> decrypt(
        @Valid @RequestBody WeComCustomerServiceDecryptRequest request) {
        return Result.success(decryptService.decrypt(request));
    }
}
