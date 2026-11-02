/**
 * 企业微信 HTTP 协议适配层。
 *
 * <p>本包仅做路径与协议适配，不合并不同信任边界的入口。能力分流见
 * {@code docs/design/wecom-capability-map.md}。</p>
 *
 * <ul>
 *     <li>{@link com.g2rain.iam.controller.wecom.WeComOAuthController} — 扫码登录 / Stream 发码</li>
 *     <li>{@link com.g2rain.iam.controller.wecom.WeComAuthorizationCallbackController} — 第三方应用安装授权回调（企微公网）</li>
 *     <li>{@link com.g2rain.iam.controller.wecom.WeComCustomerServiceController} — 客服回调解密（受信服务调用）</li>
 * </ul>
 */
package com.g2rain.iam.controller.wecom;
