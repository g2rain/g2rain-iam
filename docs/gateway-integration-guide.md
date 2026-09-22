# 网关接入手册

状态：**目标接入契约，须在 OAuth 升级实施并发布后启用**  
适用对象：需要代表某个 G2rain 租户经网关调用 API 的第三方 Web、服务端或桌面应用开发者。

## 1. 选择正确的授权方式

| 场景 | 使用方式 | 是否有管理员浏览器交互 |
| --- | --- | --- |
| 第三方产品代表租户调用 API | 既有授权码 + Client/Application DPoP | 是 |
| 用户登录 G2rain 自有前端 | 使用平台既有登录接入 | 不属于本手册范围 |

不要让浏览器保存 `client_secret`，不要使用密码模式，也不要由客户端自行制造授权码。

## 2. 接入前准备

请向网关运营人员申请并完成：

1. 创建网关接入应用，取得 `applicationCode` 并配置应用公钥；私钥绝不通过工单或聊天传递。
2. 准备回调地址 `redirectUri`；生产环境使用 HTTPS。
3. 客户端实例生成 DPoP 密钥对和 `clientId`；应用服务能生成 Application DPoP。
4. 由租户管理员在授权页面确认开通相应应用能力。

下例用占位符表示环境地址。请使用运营方发放的实际 IAM 基地址和 API Gateway 基地址：

```text
IAM_BASE_URL=https://iam.example.com
API_BASE_URL=https://api.example.com
CLIENT_ID=example-client-instance-id
APPLICATION_CODE=example-open-application
REDIRECT_URI=https://client.example.com/oauth/callback
```

## 3. 发起租户授权

将用户浏览器重定向到授权端点。客户端持有 DPoP 私钥，并生成、保存高熵 `state`。

```http
GET {IAM_BASE_URL}/auth/authorize?responseType=code
  &clientId={CLIENT_ID}
  &redirectUri={URL_ENCODED_REDIRECT_URI}
  &applicationCode={APPLICATION_CODE}
  &state={HIGH_ENTROPY_STATE}
  HTTP/1.1
```

管理员将在 IAM 中登录、选择所属租户用户并确认开通目标网关应用。用户拒绝或校验失败时，客户端应安全处理错误回调，不应将错误详情直接展示为系统内部信息。

成功时，IAM 重定向回预登记地址：

```text
https://client.example.com/oauth/callback?code={AUTHORIZATION_CODE}&state={HIGH_ENTROPY_STATE}
```

回调处理要求：

- 首先比较返回 `state` 与本地会话中值；不一致立刻中止。
- code 是短期、一次性凭据，只能由服务端使用；不得写日志、URL 埋点或前端存储。
- 立即在服务端交换 code；不要把 code 转发给其他页面或服务。

## 4. 用 code 换取 Token

服务端向 Token 端点提交表单数据。Client DPoP 的 `kid` 必须等于发起授权时的 `clientId`，其 `acd` 必须等于 `applicationCode`；Application DPoP 必须由目标应用配置的私钥签名。

```http
POST {IAM_BASE_URL}/auth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded
DPoP: {CLIENT_DPOP_PROOF}
application-DPoP: {APPLICATION_DPOP_PROOF}

grantType=authorization_code
&code={AUTHORIZATION_CODE}
```

Token 端点的具体 DPoP 证明格式、应用级证明要求和响应字段以运营方发布的客户端登记资料为准。现有 IAM 已对 `/auth/token` 执行客户端 DPoP 校验；接入方不得以空 Header、伪造 JWK 或固定 proof 绕过校验。

成功响应将包含访问 Token；若该客户端和租户策略允许，也可能包含刷新所需凭据。将凭据仅保存于服务端加密存储，不要返回给浏览器、移动端日志或分析系统。

## 5. Java 与 JavaScript 接入组件

### 5.1 建议依赖与职责

| 运行环境 | 推荐组件 | 负责内容 |
| --- | --- | --- |
| Java / Spring Boot | `com.nimbusds:nimbus-jose-jwt`、JDK `HttpClient` 或 Spring `WebClient` | 管理 ES256 密钥、生成 Client DPoP、服务端换票 |
| 浏览器 / TypeScript | `jose`、`js-sha256` | 生成 Client DPoP 密钥和每次请求的 DPoP proof；发起浏览器授权跳转 |
| 应用后端 | KMS/HSM 或受控密钥服务 | 保存应用私钥并生成 `application-DPoP`；私钥绝不下发浏览器 |

DPoP 的 `pha` 计算规则与平台现有实现一致：`SHA-256(规范化查询串 + "\\n" + SHA-256(请求体原始字节的十六进制值))`。签名前必须固定最终 URL、查询串和请求体，禁止在签名后再由 HTTP 客户端改写。

### 5.2 Java：Client DPoP 工具类

以下组件使用 Nimbus 生成与现有 IAM 匹配的 `ES256` proof。`clientId` 放入 Header 的 `kid`，`applicationCode` 放入 Claim `acd`。

```java
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class G2rainDpop {
    private G2rainDpop() { }

    public static ECKey generateClientKey(String clientId) throws JOSEException {
        return new ECKeyGenerator(Curve.P_256)
            .keyID(clientId)
            .generate();
    }

    public static String sign(
        ECKey privateKey,
        String clientId,
        String applicationCode,
        String htu,
        String method,
        String canonicalQuery,
        byte[] requestBody
    ) throws JOSEException {
        ECKey publicJwk = privateKey.toPublicJWK();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("dpop+jwt"))
            .jwk(publicJwk)
            .keyID(clientId)
            .customParam("ph_alg", "SHA-256")
            .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .claim("htu", htu)
            .claim("htm", method.toUpperCase())
            .claim("acd", applicationCode)
            .claim("pha", payloadHash(canonicalQuery, requestBody))
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(privateKey));
        return jwt.serialize();
    }

    private static String payloadHash(String query, byte[] body) {
        return sha256Hex((query + "\\n" + sha256Hex(body))
            .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

服务端换票时，先构造表单的最终字节内容，再用同一份字节计算 DPoP。`application-DPoP` 应由应用后端的受保护私钥生成；不要把该私钥嵌入桌面端、浏览器或移动端。

```java
String body = "grantType=authorization_code&code="
    + URLEncoder.encode(code, StandardCharsets.UTF_8);
String dpop = G2rainDpop.sign(clientKey, clientId, applicationCode,
    "/auth/token", "POST", "", body.getBytes(StandardCharsets.UTF_8));

HttpRequest request = HttpRequest.newBuilder(URI.create(iamBaseUrl + "/auth/token"))
    .header("Content-Type", "application/x-www-form-urlencoded")
    .header("DPoP", dpop)
    .header("application-DPoP", applicationProofSigner.sign(body, dpop))
    .POST(HttpRequest.BodyPublishers.ofString(body))
    .build();
```

`applicationProofSigner` 应是接入方自己的服务接口或 KMS 适配器；其具体签名入口由应用部署方式决定。

### 5.3 JavaScript：授权跳转与 Client DPoP

前端只负责生成 Client DPoP 密钥与浏览器跳转。访问 Token、刷新凭据和 Application DPoP 均应由服务端保管与处理。

```ts
import { exportJWK, generateKeyPair, importJWK, SignJWT, type JWK } from 'jose';
import { sha256 } from 'js-sha256';

export async function createClient(clientId: string) {
  const keys = await generateKeyPair('ES256', { extractable: true });
  return {
    clientId,
    publicKey: await exportJWK(keys.publicKey),
    privateKey: await exportJWK(keys.privateKey),
  };
}

export async function signDpop(
  client: { clientId: string; publicKey: JWK; privateKey: JWK },
  applicationCode: string, htu: string, method: string,
  query: string, body: ArrayBuffer,
) {
  const pha = sha256(`${query}\\n${sha256(body)}`);
  const signingKey = await importJWK(client.privateKey, 'ES256');
  return new SignJWT({ htu, htm: method.toUpperCase(), acd: applicationCode,
    pha, jti: crypto.randomUUID() })
    .setProtectedHeader({ typ: 'dpop+jwt', alg: 'ES256', ph_alg: 'SHA-256',
      jwk: client.publicKey, kid: client.clientId })
    .setIssuedAt()
    .setExpirationTime('5m')
    .sign(signingKey);
}

export function beginAuthorization(input: {
  iamBaseUrl: string; clientId: string; redirectUri: string;
  applicationCode: string; state: string;
}) {
  const url = new URL('/auth/authorize', input.iamBaseUrl);
  url.search = new URLSearchParams({ responseType: 'code', clientId: input.clientId,
    redirectUri: input.redirectUri, applicationCode: input.applicationCode,
    state: input.state }).toString();
  window.location.assign(url);
}
```

项目内 Main Shell 已有同协议的 DPoP 签名实现；接入方应自行实现或封装上述组件，不应依赖 Main Shell 的私有构建产物。

## 6. 调用网关 API

每个请求带上 Bearer Token 和当前请求对应的 DPoP proof：

```http
GET {API_BASE_URL}/gateway-api/orders HTTP/1.1
Authorization: Bearer {ACCESS_TOKEN}
DPoP: {REQUEST_DPOP_PROOF}
Accept: application/json
```

Token 只表示获批的租户和 scope，并不绕过资源级权限、数据隔离、配额或限流。调用失败时请根据稳定错误码处理：重新认证、请求更小范围、请管理员重新开通，或遵从 `Retry-After`；不要通过无限重试规避限流。

## 7. Token 刷新、撤销与轮换

- 仅在服务端使用刷新凭据；刷新请求仍需提供客户端 DPoP，并沿用现有登录状态校验。
- 收到授权码失效、应用未开通或租户授权关闭后，删除本地凭据并重新发起管理员授权；不要重复使用旧 code 或 refresh token。
- 怀疑泄露时立刻在网关接入控制台撤销授权/轮换客户端密钥，并联系运营支持。撤销后已有 access token 会按平台公布的短有效期或撤销策略失效。

## 8. 必须遵守的安全要求

1. 使用 HTTPS；回调地址必须为预登记精确值。
2. 对所有换票和 API 请求使用 DPoP：Client DPoP 的 `kid` 与 `acd` 要与授权事务一致，Application DPoP 使用应用私钥签名；并验证 `state`。
3. 私钥、client secret、access token、refresh token、授权码不得出现在 Git、浏览器存储、错误报告或日志。
4. 每次请求生成新的 DPoP proof，并正确设置 `htm`、规范化 `htu`、`iat`、`jti`；不得重放 proof。
5. 使用最小 scope；仅请求产品真正需要的 API 权限。
6. 妥善处理 `401`、`403`、`429` 与网络超时；严禁以并发重试或凭据共享绕过访问控制。

## 9. 上线检查表

- [ ] 已登记生产 `redirect_uri`，且回调地址使用 HTTPS。
- [ ] 已实现 `state` 校验和一次性 code 服务端交换。
- [ ] 已实现 DPoP 生成、每请求 proof 和安全凭据存储。
- [ ] 租户管理员已开通应用能力并批准所需 scope。
- [ ] 已覆盖管理员拒绝、state 错误、`clientId` 或 `acd` 不匹配、code 重放、应用停用和限流场景。
- [ ] 日志与监控已验证不包含 code、Token、secret、私钥或完整 DPoP。

## 10. 支持信息

提交接入支持请求时，请提供客户端 ID、环境、时间范围、请求关联 ID 和脱敏错误码。不要提供 access token、refresh token、授权码、私钥或 client secret。
