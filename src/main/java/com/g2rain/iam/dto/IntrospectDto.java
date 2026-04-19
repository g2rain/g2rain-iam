package com.g2rain.iam.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author alpha
 * @since 2025/10/15
 */
@Setter
@Getter
@NoArgsConstructor
public class IntrospectDto {
    @NotBlank
    @JsonProperty(value = "jwt")
    private String jwt;
}
