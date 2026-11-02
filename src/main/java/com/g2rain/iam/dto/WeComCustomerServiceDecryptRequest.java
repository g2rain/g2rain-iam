package com.g2rain.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WeComCustomerServiceDecryptRequest {

    @NotBlank
    private String callbackType;

    @NotBlank
    private String bindingCode;

    @NotBlank
    private String msgSignature;

    @NotBlank
    private String timestamp;

    @NotBlank
    private String nonce;

    @NotBlank
    private String encryptedBody;
}
