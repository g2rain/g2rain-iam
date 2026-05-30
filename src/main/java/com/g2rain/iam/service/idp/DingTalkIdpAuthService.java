package com.g2rain.iam.service.idp;


import com.g2rain.basis.dto.PassportDto;
import com.g2rain.basis.dto.PassportIdpBindingDto;
import com.g2rain.basis.dto.PassportIdpBindingSelectDto;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.vo.PassportIdpBindingVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.PassportIdpBindingClient;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;
import com.g2rain.iam.service.PassportService;
import com.g2rain.iam.utils.Constants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 钉钉身份源：绑定查询、自动注册 passport、写入 passport_idp_binding。
 */
@Slf4j
@Service
public class DingTalkIdpAuthService implements IdpAuthService {

    @Resource
    private PassportIdpBindingClient passportIdpBindingClient;

    @Resource
    private PassportService passportService;

    @Override
    public boolean supports(String idpType) {
        return IdpType.DINGTALK.name().equals(idpType);
    }

    @Override
    public String resolvePassportId(IdpPrincipal principal, boolean autoProvisionMissingPassport) {
        if (!supports(principal.idpType())) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType");
        }

        String idpApplicationCode = principal.idpApplicationCode() == null
            ? ""
            : principal.idpApplicationCode().trim();
        PassportIdpBindingSelectDto query = new PassportIdpBindingSelectDto();
        query.setIdpType(IdpType.DINGTALK.name());
        query.setIdpSubject(principal.idpSubject());
        query.setIdpApplicationCode(idpApplicationCode);

        Result<List<PassportIdpBindingVo>> result;
        try {
            result = passportIdpBindingClient.selectList(query);
        } catch (Exception e) {
            log.error("passport_idp_binding lookup failed idpSubject={} idpApplicationCode={}",
                principal.idpSubject(), idpApplicationCode, e);
            throw new BusinessException(IamErrorCode.DINGTALK_IDP_BINDING_LOOKUP_FAILED);
        }
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }

        String passportId;
        if (Collections.isNotEmpty(result.getData())) {
            passportId = Objects.toString(result.getData().getFirst().getPassportId(), null);
        } else if (!autoProvisionMissingPassport) {
            throw new BusinessException(IamErrorCode.DINGTALK_STREAM_USER_NOT_BOUND);
        } else {
            passportId = registerPassportAndIdpBinding(principal, idpApplicationCode, query);
        }
        requireNonBlankPassportId(passportId);
        return passportId;
    }

    private static void requireNonBlankPassportId(String passportId) {
        if (Strings.isBlank(passportId)) {
            throw new BusinessException(IamErrorCode.DINGTALK_SESSION_PASSPORT_MISSING);
        }
    }

    private String registerPassportAndIdpBinding(
        IdpPrincipal principal,
        String idpApplicationCode,
        PassportIdpBindingSelectDto bindingQuery
    ) {
        String username = dingTalkPassportUsername(principal.idpSubject());
        PassportDto passportDto = new PassportDto();
        passportDto.setUsername(username);
        passportDto.setPassword(Constants.THIRD_PARTY_IDP_AUTO_REGISTER_PASSPORT_PASSWORD);
        String realName = Strings.isBlank(principal.displayName()) ? "钉钉用户" : principal.displayName().trim();
        if (realName.length() > 128) {
            realName = realName.substring(0, 128);
        }
        passportDto.setRealName(realName);
        passportDto.setPasswordTrusted(false);

        Result<?> passportSave = passportService.register(passportDto);
        if (!passportSave.isSuccess()) {
            Result<List<PassportIdpBindingVo>> again = passportIdpBindingClient.selectList(bindingQuery);
            if (again != null && again.isSuccess() && Collections.isNotEmpty(again.getData())) {
                return Objects.toString(again.getData().getFirst().getPassportId(), null);
            }
            throw ExceptionConverter.of(passportSave);
        }
        Long newPassportId = (Long) passportSave.getData();

        PassportIdpBindingDto bindingDto = new PassportIdpBindingDto();
        bindingDto.setPassportId(newPassportId);
        bindingDto.setIdpType(IdpType.DINGTALK.name());
        bindingDto.setIdpSubject(principal.idpSubject());
        bindingDto.setCorpId(principal.corpId());
        bindingDto.setIdpUserId(principal.idpUserId());
        bindingDto.setIdpApplicationCode(idpApplicationCode);
        bindingDto.setBindMode(principal.bindMode());
        bindingDto.setRawProfile(Strings.isBlank(principal.rawProfile()) ? "{}" : principal.rawProfile());

        Result<Long> bindingSave = passportIdpBindingClient.save(bindingDto);
        if (!bindingSave.isSuccess()) {
            throw ExceptionConverter.of(bindingSave);
        }
        return Objects.toString(newPassportId, null);
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
