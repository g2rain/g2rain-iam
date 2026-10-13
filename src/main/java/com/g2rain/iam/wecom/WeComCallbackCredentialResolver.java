package com.g2rain.iam.wecom;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.springframework.stereotype.Component;

/**
 * 按回调类型与绑定标识解析解密凭据。
 */
@Component
public class WeComCallbackCredentialResolver {

    private final WeComIamProperties properties;

    public WeComCallbackCredentialResolver(WeComIamProperties properties) {
        this.properties = properties;
    }

    public WeComCallbackCredential resolve(
        WeComCallbackType callbackType, String bindingCode) {
        if (callbackType == null) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
        return switch (callbackType) {
            case THIRD_PARTY_AUTHORIZATION -> resolveThirdPartyAuthorization();
            case CUSTOMER_SERVICE -> resolveCustomerService(bindingCode);
        };
    }

    private WeComCallbackCredential resolveThirdPartyAuthorization() {
        WeComIamProperties.ThirdParty thirdParty = properties.getThirdParty();
        requireConfigured(thirdParty.getToken(), "thirdParty.token");
        requireConfigured(thirdParty.getEncodingAesKey(), "thirdParty.encodingAesKey");
        requireConfigured(thirdParty.getSuiteId(), "thirdParty.suiteId");
        return new WeComCallbackCredential(
            WeComCallbackType.THIRD_PARTY_AUTHORIZATION,
            "third-party-authorization",
            thirdParty.getToken().trim(),
            thirdParty.getEncodingAesKey().trim(),
            thirdParty.getSuiteId().trim(),
            null,
            IdpBindMode.THIRD_PARTY.name()
        );
    }

    private WeComCallbackCredential resolveCustomerService(String bindingCode) {
        WeComIamProperties.CustomerService customerService = properties.getCustomerService();
        if (!customerService.isEnabled()) {
            throw new BusinessException(IamErrorCode.WECOM_CS_BINDING_NOT_FOUND);
        }
        if (Strings.isBlank(bindingCode)) {
            throw new BusinessException(IamErrorCode.WECOM_CS_BINDING_NOT_FOUND);
        }
        String normalized = bindingCode.trim();
        for (WeComIamProperties.CustomerServiceBinding binding : customerService.getBindings()) {
            if (binding == null || Strings.isBlank(binding.getBindingCode())) {
                continue;
            }
            if (!normalized.equals(binding.getBindingCode().trim())) {
                continue;
            }
            requireConfigured(binding.getToken(), "customerService.token");
            requireConfigured(binding.getEncodingAesKey(), "customerService.encodingAesKey");
            requireConfigured(binding.getExpectedReceiver(), "customerService.expectedReceiver");
            String bindMode = Strings.isBlank(binding.getBindMode())
                ? IdpBindMode.THIRD_PARTY.name()
                : binding.getBindMode().trim();
            IdpBindMode.validate(bindMode);
            return new WeComCallbackCredential(
                WeComCallbackType.CUSTOMER_SERVICE,
                normalized,
                binding.getToken().trim(),
                binding.getEncodingAesKey().trim(),
                binding.getExpectedReceiver().trim(),
                Strings.isBlank(binding.getEnterpriseId()) ? null : binding.getEnterpriseId().trim(),
                bindMode
            );
        }
        throw new BusinessException(IamErrorCode.WECOM_CS_BINDING_NOT_FOUND);
    }

    private static void requireConfigured(String value, String field) {
        if (Strings.isBlank(value)) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING, field);
        }
    }
}
