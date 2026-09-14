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

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/wecom/customer_service")
@Tag(name = "企业微信客服回调", description = "客服模块回调解密与定租户")
public class WeComCustomerServiceController {

    private final WeComCustomerServiceDecryptService decryptService;

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
