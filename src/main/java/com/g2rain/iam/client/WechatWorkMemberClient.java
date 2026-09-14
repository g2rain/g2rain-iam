package com.g2rain.iam.client;

import com.g2rain.member.api.WechatWorkMemberInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * IAM → Member 企业微信会员解析客户端。
 * <p>通过服务发现在受信服务网络内无鉴权直连 Member。Member 信任 IAM 传入的租户事实，
 * 因此 Member 服务不得暴露到非受信网络。</p>
 */
@FeignClient(
    name = "g2rain-member",
    contextId = "wechatWorkMemberClient",
    path = "/internal/wechat_work_member"
)
public interface WechatWorkMemberClient extends WechatWorkMemberInternalApi {
}
