package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WeComOAuthStateDto {
    private String bindMode;
    private String clientId;
    private String redirectUri;
    private String state;
}
