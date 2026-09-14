package com.g2rain.iam.service.idp;

import com.g2rain.basis.dto.IdpEmployeeEnsureRequest;
import com.g2rain.basis.dto.IdpEnterpriseOrganResolveRequest;
import com.g2rain.basis.vo.IdpEmployeeEnsureVo;
import com.g2rain.basis.vo.IdpEnterpriseOrganResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.IdpEmployeeClient;
import com.g2rain.iam.client.IdpEnterpriseOrganClient;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.idp.IdpPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * IdP 员工扫码登录闸门：按 loginRole 解析 organ、ensure User。
 */
@Service
@RequiredArgsConstructor
public class IdpEmployeeLoginOrchestrator {

    private final IdpAuthServiceRouter idpAuthServiceRouter;
    private final IdpEnterpriseOrganClient organClient;
    private final IdpEmployeeClient employeeClient;

    /**
     * 解析 passport，并按 loginRole 完成 organ/User 闸门。
     *
     * @return passportId（字符串）
     */
    public String resolvePassportForEmployeeLogin(
        IdpPrincipal principal, IdpLoginRole loginRole, boolean autoProvisionMissingPassport) {
        String passportId = idpAuthServiceRouter.resolvePassportId(principal, autoProvisionMissingPassport);
        if (loginRole == IdpLoginRole.ADMIN) {
            ensureOrganUserIfMapped(principal, passportId);
            return passportId;
        }
        // USER：必须已有企业映射
        Long organId = requireOrganId(principal);
        ensureEmployeeUser(organId, passportId, principal.displayName());
        return passportId;
    }

    private void ensureOrganUserIfMapped(IdpPrincipal principal, String passportId) {
        Long organId = resolveOrganIdOrNull(principal);
        if (organId == null) {
            return;
        }
        ensureEmployeeUser(organId, passportId, principal.displayName());
    }

    private Long requireOrganId(IdpPrincipal principal) {
        Long organId = resolveOrganIdOrNull(principal);
        if (organId == null) {
            throw new BusinessException(IamErrorCode.IDP_ENTERPRISE_ORGAN_NOT_READY);
        }
        return organId;
    }

    private Long resolveOrganIdOrNull(IdpPrincipal principal) {
        if (Strings.isBlank(principal.corpId())) {
            return null;
        }
        IdpEnterpriseOrganResolveRequest request = new IdpEnterpriseOrganResolveRequest();
        request.setIdpType(principal.idpType());
        request.setEnterpriseId(principal.corpId().trim());
        request.setBindMode(principal.bindMode());
        Result<IdpEnterpriseOrganResolveVo> result = organClient.resolve(request);
        if (!result.isSuccess()) {
            // 未找到映射：USER 路径视为未初始化；ADMIN 路径可忽略
            if (isNotFound(result)) {
                return null;
            }
            throw ExceptionConverter.of(result);
        }
        IdpEnterpriseOrganResolveVo data = result.getData();
        return data == null ? null : data.getOrganId();
    }

    private static boolean isNotFound(Result<?> result) {
        String code = result.getErrorCode();
        return code != null && (
            code.contains("IDP_ENTERPRISE_ORGAN_NOT_FOUND")
                || "basis.40068".equals(code)
        );
    }

    private void ensureEmployeeUser(Long organId, String passportId, String displayName) {
        IdpEmployeeEnsureRequest request = new IdpEmployeeEnsureRequest();
        request.setOrganId(organId);
        request.setPassportId(Long.valueOf(passportId));
        request.setRealName(displayName);
        Result<IdpEmployeeEnsureVo> result = employeeClient.ensure(request);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
    }
}
