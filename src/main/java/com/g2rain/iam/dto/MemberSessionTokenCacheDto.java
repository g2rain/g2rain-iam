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
    /** 换票时 Client DPoP kid，复用须与当前客户端一致 */
    private String clientId;
    /** 换票时 Client DPoP 公钥 JSON，复用须与当前客户端绑钥一致 */
    private String clientPublicKey;
}
