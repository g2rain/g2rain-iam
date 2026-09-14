package com.g2rain.iam.dto;

import com.g2rain.member.dto.WechatWorkExternalProfileDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberAuthorizeTokenRequest {

    @NotBlank
    private String memberResolveCode;

    @NotBlank
    private String externalUserId;

    private String msgid;

    @Valid
    private WechatWorkExternalProfileDto externalProfile;
}
