package com.g2rain.iam.service.idp;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.idp.IdpPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 {@link IdpPrincipal#idpType()} 路由到具体 {@link IdpAuthService} 实现。
 */
@Component
@RequiredArgsConstructor
public class IdpAuthServiceRouter {

    private final List<IdpAuthService> idpAuthServices;

    public String resolvePassportId(IdpPrincipal principal, boolean autoProvisionMissingPassport) {
        if (principal == null || Strings.isBlank(principal.idpType())) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType");
        }
        return idpAuthServices.stream()
            .filter(service -> service.supports(principal.idpType()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType"))
            .resolvePassportId(principal, autoProvisionMissingPassport);
    }
}
