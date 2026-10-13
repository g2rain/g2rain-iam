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

    /**
     * 按 idpType 路由到具体通讯录同步实现。
     */
    @Resource
    private IdpContactSyncRouter idpContactSyncRouter;

    /**
     * 拉取 IdP 组织/部门快照。
     *
     * @param request 含 idpType 与渠道参数的快照请求
     * @return 组织快照
     */
    @Override
    public Result<IdpOrganizationSnapshot> fetchSnapshot(@RequestBody @Validated IdpFetchSnapshotRequest request) {
        return Result.success(idpContactSyncRouter.fetchSnapshot(request.getIdpType(), request));
    }

    /**
     * 拉取 IdP 单个成员节点详情。
     *
     * @param request 含 idpType 与成员标识的请求
     * @return 成员节点
     */
    @Override
    public Result<IdpMemberNode> fetchMember(@RequestBody @Validated IdpFetchMemberRequest request) {
        return Result.success(idpContactSyncRouter.fetchMember(request.getIdpType(), request));
    }
}
