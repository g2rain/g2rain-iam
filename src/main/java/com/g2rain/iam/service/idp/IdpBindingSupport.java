package com.g2rain.iam.service.idp;


import com.g2rain.basis.dto.PassportIdpBindingDto;
import com.g2rain.basis.dto.PassportIdpBindingSelectDto;
import com.g2rain.basis.vo.PassportIdpBindingVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.PassportIdpBindingClient;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * IdP 与 passport 绑定表的通用读写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdpBindingSupport {

    private final PassportIdpBindingClient passportIdpBindingClient;

    /**
     * 按 IdP 主体查已有绑定，无记录返回 {@link Optional#empty()}。
     */
    public Optional<String> lookupBinding(IdpPrincipal principal) {
        PassportIdpBindingSelectDto query = buildBindingQuery(principal);
        Result<List<PassportIdpBindingVo>> result;
        try {
            result = passportIdpBindingClient.selectList(query);
        } catch (Exception e) {
            log.error("passport_idp_binding lookup failed idpType={} idpSubject={} idpApplicationCode={}",
                principal.idpType(), principal.idpSubject(), query.getIdpApplicationCode(), e);
            throw new BusinessException(IamErrorCode.IDP_BINDING_LOOKUP_FAILED);
        }
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        if (Collections.isEmpty(result.getData())) {
            return Optional.empty();
        }
        return Optional.ofNullable(Objects.toString(result.getData().getFirst().getPassportId(), null));
    }

    /**
     * 写入 passport 与 IdP 主体的绑定关系。
     */
    public void saveBinding(long passportId, IdpPrincipal principal) {
        String idpApplicationCode = normalizeIdpApplicationCode(principal);

        PassportIdpBindingDto bindingDto = new PassportIdpBindingDto();
        bindingDto.setPassportId(passportId);
        bindingDto.setIdpType(principal.idpType());
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
    }

    public static void requireNonBlankPassportId(String passportId) {
        if (Strings.isBlank(passportId)) {
            throw new BusinessException(IamErrorCode.IDP_SESSION_PASSPORT_MISSING);
        }
    }

    private static PassportIdpBindingSelectDto buildBindingQuery(IdpPrincipal principal) {
        PassportIdpBindingSelectDto query = new PassportIdpBindingSelectDto();
        query.setIdpType(principal.idpType());
        query.setIdpSubject(principal.idpSubject());
        query.setIdpApplicationCode(normalizeIdpApplicationCode(principal));
        return query;
    }

    private static String normalizeIdpApplicationCode(IdpPrincipal principal) {
        return principal.idpApplicationCode() == null ? "" : principal.idpApplicationCode().trim();
    }
}
