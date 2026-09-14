package com.g2rain.iam.service;

import com.g2rain.basis.dto.IdpEnterpriseApplicationAuthorizationResolveRequest;
import com.g2rain.basis.dto.IdpEnterpriseOrganResolveRequest;
import com.g2rain.basis.enums.IdpApplicationAuthorizationStatus;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.vo.IdpEnterpriseApplicationAuthorizationResolveVo;
import com.g2rain.basis.vo.IdpEnterpriseOrganResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.IdpEnterpriseApplicationAuthorizationClient;
import com.g2rain.iam.client.IdpEnterpriseOrganClient;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.dto.WeComCustomerServiceDecryptRequest;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.vo.WeComCustomerServiceDecryptVo;
import com.g2rain.iam.wecom.VerifiedWeComCallback;
import com.g2rain.iam.wecom.WeComCallbackCredential;
import com.g2rain.iam.wecom.WeComCallbackCredentialResolver;
import com.g2rain.iam.wecom.WeComCallbackCrypto;
import com.g2rain.iam.wecom.WeComCallbackType;
import com.g2rain.iam.wecom.WeComCallbackVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeComCustomerServiceDecryptService {

    private static final DateTimeFormatter OFFSET_FORMATTER =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final WeComCallbackCredentialResolver credentialResolver;
    private final WeComCallbackVerifier callbackVerifier;
    private final IdpEnterpriseApplicationAuthorizationClient authorizationClient;
    private final IdpEnterpriseOrganClient organClient;
    private final MemberResolveCodeService memberResolveCodeService;
    private final WeComIamProperties weComIamProperties;

    public WeComCustomerServiceDecryptVo decrypt(WeComCustomerServiceDecryptRequest request) {
        WeComCallbackType callbackType = parseCallbackType(request.getCallbackType());
        if (callbackType != WeComCallbackType.CUSTOMER_SERVICE) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID, "callbackType");
        }

        WeComCallbackCredential credential = credentialResolver.resolve(
            callbackType, request.getBindingCode());
        VerifiedWeComCallback verified = callbackVerifier.verifyAndDecrypt(
            credential,
            request.getMsgSignature(),
            request.getTimestamp(),
            request.getNonce(),
            request.getEncryptedBody()
        );

        String enterpriseId = resolveEnterpriseId(credential, verified.plainBody());
        ensureActiveAuthorization(enterpriseId, credential.bindMode());
        Long organId = resolveOrganId(enterpriseId, credential.bindMode());

        MemberResolveCodeDto codePayload = new MemberResolveCodeDto();
        codePayload.setOrganId(organId);
        codePayload.setBindingCode(credential.bindingCode());
        codePayload.setEnterpriseId(enterpriseId);
        codePayload.setCallbackType(callbackType.name());
        codePayload.setBindMode(credential.bindMode());
        MemberResolveCodeService.IssuedCode issued = memberResolveCodeService.issue(codePayload);

        WeComCustomerServiceDecryptVo vo = new WeComCustomerServiceDecryptVo();
        vo.setVerified(true);
        vo.setOrganId(organId);
        vo.setEnterpriseId(enterpriseId);
        vo.setCallbackType(callbackType.name());
        vo.setPlainBody(verified.plainBody());
        vo.setMemberResolveCode(issued.code());
        vo.setCodeExpiresAt(formatInstant(issued.expiresAt().atZone(ZoneId.systemDefault())));
        vo.setVerifiedAt(formatInstant(Moments.now().atZone(ZoneId.systemDefault())));
        log.info("wecom customer_service decrypt ok bindingCode={} organId={} enterpriseIdLen={}",
            credential.bindingCode(), organId,
            enterpriseId == null ? 0 : enterpriseId.length());
        return vo;
    }

    private void ensureActiveAuthorization(String enterpriseId, String bindMode) {
        if (!IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            return;
        }
        String suiteId = weComIamProperties.getThirdParty().getSuiteId();
        if (Strings.isBlank(suiteId)) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING, "suiteId");
        }
        IdpEnterpriseApplicationAuthorizationResolveRequest request =
            new IdpEnterpriseApplicationAuthorizationResolveRequest();
        request.setIdpType(IdpType.WECHAT_WORK.name());
        request.setBindMode(IdpBindMode.THIRD_PARTY.name());
        request.setIdpApplicationCode(suiteId.trim());
        request.setEnterpriseId(enterpriseId);
        Result<IdpEnterpriseApplicationAuthorizationResolveVo> result =
            authorizationClient.resolve(request);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        IdpEnterpriseApplicationAuthorizationResolveVo authorization = result.getData();
        if (authorization == null
            || !IdpApplicationAuthorizationStatus.ACTIVE.name()
            .equals(authorization.getAuthorizationStatus())
            || Strings.isBlank(authorization.getInstalledApplicationId())) {
            throw new BusinessException(IamErrorCode.WECOM_ENTERPRISE_NOT_AUTHORIZED);
        }
    }

    private Long resolveOrganId(String enterpriseId, String bindMode) {
        IdpEnterpriseOrganResolveRequest request = new IdpEnterpriseOrganResolveRequest();
        request.setIdpType(IdpType.WECHAT_WORK.name());
        request.setEnterpriseId(enterpriseId);
        request.setBindMode(bindMode);
        Result<IdpEnterpriseOrganResolveVo> result = organClient.resolve(request);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        IdpEnterpriseOrganResolveVo organ = result.getData();
        if (organ == null || organ.getOrganId() == null) {
            throw new BusinessException(IamErrorCode.WECOM_CS_ORGAN_RESOLVE_FAILED);
        }
        return organ.getOrganId();
    }

    private static String resolveEnterpriseId(
        WeComCallbackCredential credential, String plainBody) {
        if (Strings.isNotBlank(credential.enterpriseId())) {
            return credential.enterpriseId().trim();
        }
        String fromAuthCorp = WeComCallbackCrypto.xmlValue(plainBody, "AuthCorpId");
        if (Strings.isNotBlank(fromAuthCorp)) {
            return fromAuthCorp.trim();
        }
        String toUserName = WeComCallbackCrypto.xmlValue(plainBody, "ToUserName");
        if (Strings.isNotBlank(toUserName)) {
            return toUserName.trim();
        }
        throw new BusinessException(IamErrorCode.WECOM_CS_ORGAN_RESOLVE_FAILED);
    }

    private static WeComCallbackType parseCallbackType(String value) {
        try {
            return WeComCallbackType.valueOf(value.trim());
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID, "callbackType");
        }
    }

    private static String formatInstant(java.time.ZonedDateTime value) {
        return OFFSET_FORMATTER.format(value);
    }
}
