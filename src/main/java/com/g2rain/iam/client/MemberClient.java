package com.g2rain.iam.client;

import com.g2rain.member.api.MemberApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * IAM → Member 查询客户端（含 MEMBER Token 刷新状态复核）。
 * <p>通过服务发现在受信服务网络内直连；调用 {@code /member/active_for_token} 时无终端会话上下文，
 * 由 Member 侧按后端调用（{@code isBackEnd}）放行。</p>
 */
@FeignClient(
    name = "g2rain-member",
    contextId = "memberClient",
    path = "/member"
)
public interface MemberClient extends MemberApi {
}
