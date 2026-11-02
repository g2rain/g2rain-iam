package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * consent 页应用授权预览展示模型（由 IAM 拼装，不依赖 Basis consent_preview）。
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsentPreviewDto {

    /**
     * 应用名称。
     */
    private String applicationName;

    /**
     * 应用描述。
     */
    private String description;

    /**
     * 当前用户所属租户名称。
     */
    private String organName;
}
