package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberSessionTokenCacheDto {
    private String accessToken;
    private Long expireAt;
    private Long memberId;
    private String memberNo;
    private String memberStatus;
}
