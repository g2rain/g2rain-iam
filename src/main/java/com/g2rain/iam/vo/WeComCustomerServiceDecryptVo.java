package com.g2rain.iam.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WeComCustomerServiceDecryptVo {
    private boolean verified;
    private Long organId;
    private String enterpriseId;
    private String callbackType;
    private String plainBody;
    private String memberResolveCode;
    private String codeExpiresAt;
    private String verifiedAt;
}
