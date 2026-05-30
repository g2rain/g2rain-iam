package com.g2rain.iam.service.idp;


import com.g2rain.basis.enums.IdpType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * 钉钉身份源：绑定查询、自动注册 passport、写入 passport_idp_binding。
 */
@Slf4j
@Service
public class DingTalkIdpAuthService implements IdpAuthService {

    private static final String DEFAULT_DISPLAY_NAME = "钉钉用户";

    @Resource
    private IdpBindingSupport idpBindingSupport;

    @Resource
    private IdpPassportProvisioner idpPassportProvisioner;

    @Override
    public boolean supports(String idpType) {
        return IdpType.DINGTALK.name().equals(idpType);
    }

    @Override
    public String resolvePassportId(IdpPrincipal principal, boolean autoProvisionMissingPassport) {
        if (!supports(principal.idpType())) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType");
        }

        Optional<String> bound = idpBindingSupport.lookupBinding(principal);
        if (bound.isPresent()) {
            String passportId = bound.get();
            IdpBindingSupport.requireNonBlankPassportId(passportId);
            return passportId;
        }
        if (!autoProvisionMissingPassport) {
            throw new BusinessException(IamErrorCode.DINGTALK_STREAM_USER_NOT_BOUND);
        }

        String username = dingTalkPassportUsername(principal.idpSubject());
        Result<?> registerResult = idpPassportProvisioner.registerPassport(
            username, principal.displayName(), DEFAULT_DISPLAY_NAME);
        if (!registerResult.isSuccess()) {
            Optional<String> again = idpBindingSupport.lookupBinding(principal);
            if (again.isPresent()) {
                String passportId = again.get();
                IdpBindingSupport.requireNonBlankPassportId(passportId);
                return passportId;
            }
            throw ExceptionConverter.of(registerResult);
        }

        Long newPassportId = (Long) registerResult.getData();
        idpBindingSupport.saveBinding(newPassportId, principal);
        String passportId = Objects.toString(newPassportId, null);
        IdpBindingSupport.requireNonBlankPassportId(passportId);
        return passportId;
    }

    private static String dingTalkPassportUsername(String idpSubject) {
        String prefix = "dt_";
        if (prefix.length() + idpSubject.length() <= 64) {
            return prefix + idpSubject;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(idpSubject.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 28);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
