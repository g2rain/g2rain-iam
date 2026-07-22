package com.g2rain.iam.controller;

import com.g2rain.basis.api.IdpSyncApi;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.idp.sync.dto.IdpFetchMemberRequest;
import com.g2rain.basis.idp.sync.dto.IdpFetchSnapshotRequest;
import com.g2rain.basis.idp.sync.dto.IdpMemberNode;
import com.g2rain.basis.idp.sync.dto.IdpOrganizationSnapshot;
import com.g2rain.common.model.Result;
import com.g2rain.iam.service.idp.sync.IdpContactSyncRouter;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IdP 通讯录同步内部接口。
 */
@RestController
@RequestMapping("/internal/idp_sync/dingtalk")
public class IdpSyncInternalController implements IdpSyncApi {

    @Resource
    private IdpContactSyncRouter idpContactSyncRouter;

    @Override
    public Result<IdpOrganizationSnapshot> fetchDingTalkSnapshot(@RequestBody @Validated IdpFetchSnapshotRequest request) {
        return Result.success(idpContactSyncRouter.fetchSnapshot(IdpType.DINGTALK.name(), request));
    }

    @Override
    public Result<IdpMemberNode> fetchDingTalkMember(@RequestBody @Validated IdpFetchMemberRequest request) {
        return Result.success(idpContactSyncRouter.fetchMember(IdpType.DINGTALK.name(), request));
    }
}
