package com.g2rain.iam.service.idp.sync;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.idp.sync.dto.IdpFetchMemberRequest;
import com.g2rain.basis.idp.sync.dto.IdpFetchSnapshotRequest;
import com.g2rain.basis.idp.sync.dto.IdpMemberNode;
import com.g2rain.basis.idp.sync.dto.IdpOrganizationSnapshot;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.dingtalk.DingTalkContactClient;
import com.g2rain.iam.enums.IamErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DingTalkContactSyncServiceTest {

    @Mock
    private DingTalkContactClient dingTalkContactClient;

    @InjectMocks
    private DingTalkContactSyncServiceImpl dingTalkContactSyncService;

    @Test
    void supports_shouldAcceptDingTalk() {
        assertTrue(dingTalkContactSyncService.supports("DINGTALK"));
    }

    @Test
    void fetchSnapshot_shouldRejectThirdPartyBindMode() {
        IdpFetchSnapshotRequest request = new IdpFetchSnapshotRequest();
        request.setCorpId("corp");
        request.setBindMode("THIRD_PARTY");
        request.setIdpApplicationCode("client");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> dingTalkContactSyncService.fetchSnapshot(request)
        );
        assertEquals(IamErrorCode.DINGTALK_CONTACT_BIND_MODE_UNSUPPORTED.code(), exception.getErrorCode());
    }

    @Test
    void fetchMember_shouldRejectThirdPartyBindMode() {
        IdpFetchMemberRequest request = new IdpFetchMemberRequest();
        request.setCorpId("corp");
        request.setBindMode("THIRD_PARTY");
        request.setIdpApplicationCode("client");
        request.setIdpUserId("userid-1");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> dingTalkContactSyncService.fetchMember(request)
        );
        assertEquals(IamErrorCode.DINGTALK_CONTACT_BIND_MODE_UNSUPPORTED.code(), exception.getErrorCode());
    }

    @Test
    void fetchSnapshot_shouldNotCallGetUserDetailWhenUnionIdPresent() {
        IdpFetchSnapshotRequest request = new IdpFetchSnapshotRequest();
        request.setCorpId("corp-internal");
        request.setBindMode("INTERNAL");
        request.setIdpApplicationCode("ding-internal-client");

        when(dingTalkContactClient.resolveAccessToken(
            IdpBindMode.INTERNAL, "ding-internal-client", "corp-internal")).thenReturn("token");
        when(dingTalkContactClient.listAllDepartments("token")).thenReturn(List.of(
            new DingTalkContactClient.DepartmentInfo(2L, 1L, "研发部", 0)
        ));
        when(dingTalkContactClient.listDepartmentUsers(eq("token"), anyLong())).thenReturn(List.of(
            new DingTalkContactClient.UserSummary(
                "userid-1", "union-1", "张三", "13800000000", "zhang@example.com", List.of(2L)
            )
        ));
        when(dingTalkContactClient.syncMetrics()).thenReturn(new DingTalkContactClient.SyncMetrics(2, 0));

        IdpOrganizationSnapshot snapshot = dingTalkContactSyncService.fetchSnapshot(request);

        assertEquals(1, snapshot.getMembers().size());
        assertEquals("union-1", snapshot.getMembers().getFirst().getUnionId());
        verify(dingTalkContactClient, never()).getUserDetail(anyString(), anyString());
    }

    @Test
    void fetchSnapshot_shouldFallbackGetUserDetailWhenUnionIdMissing() {
        IdpFetchSnapshotRequest request = new IdpFetchSnapshotRequest();
        request.setCorpId("corp-internal");
        request.setBindMode("INTERNAL");
        request.setIdpApplicationCode("ding-internal-client");

        when(dingTalkContactClient.resolveAccessToken(
            IdpBindMode.INTERNAL, "ding-internal-client", "corp-internal")).thenReturn("token");
        when(dingTalkContactClient.listAllDepartments("token")).thenReturn(List.of(
            new DingTalkContactClient.DepartmentInfo(2L, 1L, "研发部", 0)
        ));
        when(dingTalkContactClient.listDepartmentUsers(eq("token"), anyLong())).thenReturn(List.of(
            new DingTalkContactClient.UserSummary(
                "userid-1", null, "张三", "13800000000", "zhang@example.com", List.of(2L)
            )
        ));
        when(dingTalkContactClient.getUserDetail("token", "userid-1")).thenReturn(
            new DingTalkContactClient.UserDetail("userid-1", "union-1", "张三", "13800000000", "zhang@example.com")
        );
        when(dingTalkContactClient.syncMetrics()).thenReturn(new DingTalkContactClient.SyncMetrics(3, 0));

        IdpOrganizationSnapshot snapshot = dingTalkContactSyncService.fetchSnapshot(request);

        assertEquals("union-1", snapshot.getMembers().getFirst().getUnionId());
        verify(dingTalkContactClient).getUserDetail("token", "userid-1");
    }

    @Test
    void mergeMemberFromSummary_shouldUseDeptIdListFromApi() {
        IdpMemberNode member = new IdpMemberNode();
        member.setIdpUserId("userid-1");

        DingTalkContactClient.UserSummary user = new DingTalkContactClient.UserSummary(
            "userid-1", "union-1", "张三", null, null, List.of(2L, 3L)
        );

        DingTalkContactSyncServiceImpl.mergeMemberFromSummary(member, user);

        assertEquals("union-1", member.getUnionId());
        assertEquals(List.of("2", "3"), member.getDeptIdpDeptIds());
    }
}
