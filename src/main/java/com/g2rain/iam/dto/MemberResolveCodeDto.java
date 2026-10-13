package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberResolveCodeDto {
    private Long organId;
    private String bindingCode;
    private String enterpriseId;
    private String callbackType;
    private String bindMode;
}
