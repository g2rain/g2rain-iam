package com.g2rain.iam.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberAuthorizeTokenVo {
    private String accessToken;
    private String tokenExpiresAt;
    private Long memberId;
    private String memberNo;
    private String memberStatus;
    private Boolean newMember;
    private Boolean identityVerified;
}
