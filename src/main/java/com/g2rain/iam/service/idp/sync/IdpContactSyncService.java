package com.g2rain.iam.service.idp.sync;

import com.g2rain.basis.idp.sync.dto.IdpFetchMemberRequest;
import com.g2rain.basis.idp.sync.dto.IdpFetchSnapshotRequest;
import com.g2rain.basis.idp.sync.dto.IdpMemberNode;
import com.g2rain.basis.idp.sync.dto.IdpOrganizationSnapshot;

/**
 * IdP 通讯录同步服务抽象。
 */
public interface IdpContactSyncService {

    /**
     * 是否支持指定身份源类型。
     */
    boolean supports(String idpType);

    /**
     * 拉取企业通讯录快照。
     */
    IdpOrganizationSnapshot fetchSnapshot(IdpFetchSnapshotRequest request);

    /**
     * 按 IdP 企业内用户标识拉取成员详情。
     */
    IdpMemberNode fetchMember(IdpFetchMemberRequest request);
}
