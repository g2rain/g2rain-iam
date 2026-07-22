package com.g2rain.iam.service.idp.sync;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.idp.sync.dto.IdpDepartmentNode;
import com.g2rain.basis.idp.sync.dto.IdpFetchMemberRequest;
import com.g2rain.basis.idp.sync.dto.IdpFetchSnapshotRequest;
import com.g2rain.basis.idp.sync.dto.IdpMemberNode;
import com.g2rain.basis.idp.sync.dto.IdpOrganizationSnapshot;
import com.g2rain.basis.idp.sync.dto.IdpSnapshotFetchMeta;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.dingtalk.DingTalkContactClient;
import com.g2rain.iam.enums.IamErrorCode;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉通讯录同步实现。
 */
@Service
public class DingTalkContactSyncServiceImpl implements IdpContactSyncService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkContactSyncServiceImpl.class);

    @Resource
    private DingTalkContactClient dingTalkContactClient;

    @Override
    public boolean supports(String idpType) {
        return IdpType.DINGTALK.name().equals(idpType);
    }

    @Override
    public IdpOrganizationSnapshot fetchSnapshot(IdpFetchSnapshotRequest request) {
        long startedAt = System.currentTimeMillis();
        IdpBindMode bindMode = resolveBindMode(request.getBindMode());
        if (bindMode != IdpBindMode.INTERNAL) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_BIND_MODE_UNSUPPORTED);
        }
        dingTalkContactClient.resetSyncMetrics();
        String idpApplicationCode = dingTalkContactClient.resolveIdpApplicationCode(
            bindMode, request.getIdpApplicationCode());
        String accessToken = dingTalkContactClient.resolveAccessToken(
            bindMode, idpApplicationCode, request.getCorpId());

        List<DingTalkContactClient.DepartmentInfo> departments = dingTalkContactClient.listAllDepartments(accessToken);
        IdpOrganizationSnapshot snapshot = new IdpOrganizationSnapshot();
        snapshot.setIdpApplicationCode(idpApplicationCode);
        snapshot.setDepartments(convertDepartments(departments));
        snapshot.setMembers(buildMembers(accessToken, departments));

        DingTalkContactClient.FetchStatsSnapshot stats = dingTalkContactClient.fetchStatsSnapshot();
        int deptNodeCount = snapshot.getDepartments().size();
        int memberCount = snapshot.getMembers().size();
        snapshot.setFetchMeta(toFetchMeta(stats, deptNodeCount, memberCount));
        snapshot.setComplete(isSnapshotComplete(stats, deptNodeCount, memberCount));

        DingTalkContactClient.SyncMetrics metrics = dingTalkContactClient.syncMetrics();
        log.info(
            "dingtalk contact snapshot fetched corpId={} deptCount={} memberCount={} "
                + "complete={} apiCallCount={} retryCount={} elapsedMs={}",
            request.getCorpId(),
            snapshot.getDepartments().size(),
            snapshot.getMembers().size(),
            snapshot.isComplete(),
            metrics.apiCallCount(),
            metrics.retryCount(),
            System.currentTimeMillis() - startedAt
        );
        return snapshot;
    }

    static IdpSnapshotFetchMeta toFetchMeta(
        DingTalkContactClient.FetchStatsSnapshot stats,
        int deptNodeCount,
        int memberCount
    ) {
        IdpSnapshotFetchMeta meta = new IdpSnapshotFetchMeta();
        meta.setDeptNodeCount(deptNodeCount);
        meta.setMemberCount(memberCount);
        meta.setDeptListApiCalls(stats.deptListApiCalls());
        meta.setUserListApiCalls(stats.userListApiCalls());
        meta.setUserDetailApiCalls(stats.userDetailApiCalls());
        meta.setDeptSubListNonArrayCount(stats.deptSubListNonArrayCount());
        meta.setUserListIncompletePages(stats.userListIncompletePages());
        meta.setRetryCount(stats.retryCount());
        return meta;
    }

    static boolean isSnapshotComplete(
        DingTalkContactClient.FetchStatsSnapshot stats,
        int deptNodeCount,
        int memberCount
    ) {
        if (stats.deptSubListNonArrayCount() > 0 || stats.userListIncompletePages() > 0) {
            return false;
        }
        if (stats.deptListApiCalls() <= 0) {
            return false;
        }
        return deptNodeCount > 0 || memberCount > 0;
    }

    @Override
    public IdpMemberNode fetchMember(IdpFetchMemberRequest request) {
        IdpBindMode bindMode = resolveBindMode(request.getBindMode());
        if (bindMode != IdpBindMode.INTERNAL) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_BIND_MODE_UNSUPPORTED);
        }
        String idpApplicationCode = dingTalkContactClient.resolveIdpApplicationCode(
            bindMode, request.getIdpApplicationCode());
        String accessToken = dingTalkContactClient.resolveAccessToken(
            bindMode, idpApplicationCode, request.getCorpId());
        DingTalkContactClient.UserDetail detail = dingTalkContactClient.getUserDetail(
            accessToken, request.getIdpUserId().trim());
        if (Strings.isBlank(detail.unionId())) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_UNION_ID_MISSING, request.getIdpUserId());
        }
        IdpMemberNode member = new IdpMemberNode();
        member.setIdpUserId(detail.userId());
        member.setUnionId(detail.unionId());
        member.setName(detail.name());
        member.setMobile(detail.mobile());
        member.setEmail(detail.email());
        return member;
    }

    private static List<IdpDepartmentNode> convertDepartments(List<DingTalkContactClient.DepartmentInfo> departments) {
        List<IdpDepartmentNode> nodes = new ArrayList<>(departments.size());
        for (DingTalkContactClient.DepartmentInfo dept : departments) {
            IdpDepartmentNode node = new IdpDepartmentNode();
            node.setIdpDeptId(String.valueOf(dept.deptId()));
            node.setParentIdpDeptId(String.valueOf(dept.parentId()));
            node.setName(dept.name());
            node.setSortOrder(dept.order());
            nodes.add(node);
        }
        return nodes;
    }

    private List<IdpMemberNode> buildMembers(
        String accessToken,
        List<DingTalkContactClient.DepartmentInfo> departments
    ) {
        Set<Long> deptIds = new LinkedHashSet<>();
        deptIds.add(1L);
        departments.forEach(dept -> deptIds.add(dept.deptId()));

        Map<String, IdpMemberNode> memberByUserId = new LinkedHashMap<>();
        for (Long deptId : deptIds) {
            List<DingTalkContactClient.UserSummary> users = dingTalkContactClient.listDepartmentUsers(accessToken, deptId);
            for (DingTalkContactClient.UserSummary user : users) {
                if (Strings.isBlank(user.userId())) {
                    continue;
                }
                IdpMemberNode member = memberByUserId.computeIfAbsent(user.userId(), ignored -> newMemberFromSummary(user));
                mergeMemberFromSummary(member, user);
            }
        }

        for (IdpMemberNode member : memberByUserId.values()) {
            if (Strings.isNotBlank(member.getUnionId())) {
                continue;
            }
            DingTalkContactClient.UserDetail detail = dingTalkContactClient.getUserDetail(accessToken, member.getIdpUserId());
            mergeMemberFromDetail(member, detail);
            if (Strings.isBlank(member.getUnionId())) {
                throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_UNION_ID_MISSING, member.getIdpUserId());
            }
        }
        return new ArrayList<>(memberByUserId.values());
    }

    private static IdpMemberNode newMemberFromSummary(DingTalkContactClient.UserSummary user) {
        IdpMemberNode node = new IdpMemberNode();
        node.setIdpUserId(user.userId());
        node.setUnionId(user.unionId());
        node.setName(user.name());
        node.setMobile(user.mobile());
        node.setEmail(user.email());
        mergeDeptIds(node, user.deptIds());
        return node;
    }

    static void mergeMemberFromSummary(IdpMemberNode member, DingTalkContactClient.UserSummary user) {
        if (Strings.isBlank(member.getUnionId()) && Strings.isNotBlank(user.unionId())) {
            member.setUnionId(user.unionId());
        }
        if (Strings.isBlank(member.getName()) && Strings.isNotBlank(user.name())) {
            member.setName(user.name());
        }
        if (Strings.isBlank(member.getMobile()) && Strings.isNotBlank(user.mobile())) {
            member.setMobile(user.mobile());
        }
        if (Strings.isBlank(member.getEmail()) && Strings.isNotBlank(user.email())) {
            member.setEmail(user.email());
        }
        mergeDeptIds(member, user.deptIds());
    }

    private static void mergeMemberFromDetail(IdpMemberNode member, DingTalkContactClient.UserDetail detail) {
        if (Strings.isNotBlank(detail.unionId())) {
            member.setUnionId(detail.unionId());
        }
        if (Strings.isBlank(member.getName()) && Strings.isNotBlank(detail.name())) {
            member.setName(detail.name());
        }
        if (Strings.isBlank(member.getMobile()) && Strings.isNotBlank(detail.mobile())) {
            member.setMobile(detail.mobile());
        }
        if (Strings.isBlank(member.getEmail()) && Strings.isNotBlank(detail.email())) {
            member.setEmail(detail.email());
        }
    }

    static void mergeDeptIds(IdpMemberNode member, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            String deptKey = String.valueOf(deptId);
            if (!member.getDeptIdpDeptIds().contains(deptKey)) {
                member.getDeptIdpDeptIds().add(deptKey);
            }
        }
    }

    private static IdpBindMode resolveBindMode(String bindMode) {
        if (Strings.isBlank(bindMode)) {
            return IdpBindMode.INTERNAL;
        }
        try {
            return IdpBindMode.valueOf(bindMode.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "bindMode");
        }
    }
}
