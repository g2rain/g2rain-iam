package com.g2rain.iam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IAM 内部接口：校验 passport 是否允许创建 organ。
 */
@Getter
@Setter
@NoArgsConstructor
public class VerifyCreateOrganRequest {

    @NotNull
    private Long passportId;
}
