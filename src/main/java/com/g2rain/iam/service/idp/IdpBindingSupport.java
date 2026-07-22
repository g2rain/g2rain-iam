package com.g2rain.iam.service.idp;

import com.g2rain.basis.dto.PassportIdpBindingDto;
import com.g2rain.basis.idp.resolve.dto.IdpPassportResolveRequest;
import com.g2rain.basis.idp.resolve.vo.IdpPassportResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.IdpPassportClient;
import com.g2rain.iam.client.PassportIdpBindingClient;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private final IdpPassportClient idpPassportClient;

    /**
     * 按 IdP 主体解析 passport 与跨机构 user 列表，无记录返回 {@code null}。
     */
    public IdpPassportResolveVo resolve(IdpPrincipal principal) {
        IdpPassportResolveRequest request = buildResolveRequest(principal);
        Result<IdpPassportResolveVo> result;
        try {
            result = idpPassportClient.resolve(request);
        } catch (Exception e) {
            log.error("idp passport resolve failed idpType={} idpSubject={} idpApplicationCode={}",
                principal.idpType(), principal.idpSubject(), request.getIdpApplicationCode(), e);
            throw new BusinessException(IamErrorCode.IDP_BINDING_LOOKUP_FAILED);
        }
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        return result.getData();
    }

    /**
     * 按 IdP 主体查已有绑定，无记录返回 {@link Optional#empty()}。
     */
    public Optional<String> lookupBinding(IdpPrincipal principal) {
        IdpPassportResolveVo resolved = resolve(principal);
        if (resolved == null || resolved.getPassportId() == null) {
            return Optional.empty();
        }
        return Optional.of(Objects.toString(resolved.getPassportId(), null));
    }

    /**
     * 按会话中的 IdP 上下文解析 passport 与 user 列表。
     */
    public IdpPassportResolveVo resolve(SessionDto session) {
        if (!hasIdpContext(session)) {
            return null;
        }
        IdpPassportResolveRequest request = buildResolveRequest(session);
        Result<IdpPassportResolveVo> result;
        try {
            result = idpPassportClient.resolve(request);
        } catch (Exception e) {
            log.error("idp passport resolve failed sessionId={} idpType={} idpSubject={}",
                session.getSessionId(), session.getIdpType(), session.getIdpSubject(), e);
            throw new BusinessException(IamErrorCode.IDP_BINDING_LOOKUP_FAILED);
        }
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        return result.getData();
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
        bindingDto.setIdpOpenId(principal.idpOpenId());
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

    public static boolean hasIdpContext(SessionDto session) {
        return session != null
            && Strings.isNotBlank(session.getIdpType())
            && Strings.isNotBlank(session.getIdpSubject());
    }

    public static IdpPassportResolveRequest buildResolveRequest(IdpPrincipal principal) {
        IdpPassportResolveRequest request = new IdpPassportResolveRequest();
        request.setIdpType(principal.idpType());
        request.setIdpSubject(principal.idpSubject());
        request.setIdpApplicationCode(normalizeIdpApplicationCode(principal));
        request.setIdpUserId(principal.idpUserId());
        return request;
    }

    public     static IdpPassportResolveRequest buildResolveRequest(SessionDto session) {
        IdpPassportResolveRequest request = new IdpPassportResolveRequest();
        request.setIdpType(session.getIdpType());
        request.setIdpSubject(session.getIdpSubject());
        request.setIdpApplicationCode(normalizeIdpApplicationCode(session.getIdpApplicationCode()));
        if (Strings.isNotBlank(session.getIdpUserId())) {
            request.setIdpUserId(session.getIdpUserId().trim());
        }
        return request;
    }

    private static String normalizeIdpApplicationCode(IdpPrincipal principal) {
        return principal.idpApplicationCode() == null ? "" : principal.idpApplicationCode().trim();
    }

    private static String normalizeIdpApplicationCode(String idpApplicationCode) {
        return idpApplicationCode == null ? "" : idpApplicationCode.trim();
    }
}
