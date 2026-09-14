package com.g2rain.iam.controller;

import com.g2rain.basis.api.IdpSyncApi;
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
 * IdP 通讯录同步内部接口（按请求 idpType 路由渠道）。
 */
@RestController
@RequestMapping("/internal/idp_sync")
public class IdpSyncInternalController implements IdpSyncApi {

    @Resource
    private IdpContactSyncRouter idpContactSyncRouter;

    @Override
    public Result<IdpOrganizationSnapshot> fetchSnapshot(@RequestBody @Validated IdpFetchSnapshotRequest request) {
        return Result.success(idpContactSyncRouter.fetchSnapshot(request.getIdpType(), request));
    }

    @Override
    public Result<IdpMemberNode> fetchMember(@RequestBody @Validated IdpFetchMemberRequest request) {
        return Result.success(idpContactSyncRouter.fetchMember(request.getIdpType(), request));
    }
}
