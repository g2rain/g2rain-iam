package com.g2rain.iam.service.idp.sync;

import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.idp.sync.dto.IdpFetchMemberRequest;
import com.g2rain.basis.idp.sync.dto.IdpFetchSnapshotRequest;
import com.g2rain.basis.idp.sync.dto.IdpMemberNode;
import com.g2rain.basis.idp.sync.dto.IdpOrganizationSnapshot;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IdP 通讯录同步路由。
 */
@Component
public class IdpContactSyncRouter {

    @Resource
    private List<IdpContactSyncService> contactSyncServices;

    public IdpOrganizationSnapshot fetchSnapshot(String idpType, IdpFetchSnapshotRequest request) {
        String normalized = Strings.isBlank(idpType) ? IdpType.DINGTALK.name() : idpType.trim();
        for (IdpContactSyncService service : contactSyncServices) {
            if (service.supports(normalized)) {
                return service.fetchSnapshot(request);
            }
        }
        throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType");
    }

    public IdpMemberNode fetchMember(String idpType, IdpFetchMemberRequest request) {
        String normalized = Strings.isBlank(idpType) ? IdpType.DINGTALK.name() : idpType.trim();
        for (IdpContactSyncService service : contactSyncServices) {
            if (service.supports(normalized)) {
                return service.fetchMember(request);
            }
        }
        throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "idpType");
    }
}
