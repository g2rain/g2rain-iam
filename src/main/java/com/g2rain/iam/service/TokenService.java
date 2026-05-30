package com.g2rain.iam.service;


import com.g2rain.basis.dto.ApplicationSelectDto;
import com.g2rain.basis.dto.LoginTokenDto;
import com.g2rain.basis.vo.ApplicationVo;
import com.g2rain.basis.vo.PublicKeyDescriptorVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.ApplicationClient;
import com.g2rain.iam.client.LoginTokenClient;
import com.g2rain.iam.dto.AuthorizationCodeDto;
import com.g2rain.iam.dto.GenerateTokenDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.ESAlgorithm;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.enums.TokenGrantType;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUtils;
import com.g2rain.iam.vo.TokenVo;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * 令牌服务，提供基于 OAuth2 模式生成 JWT 令牌的功能。
 * <p>
 * 支持授权码模式（authorization_code）、客户端模式（client_credentials）以及刷新令牌模式（refresh_token）。
 * 使用 {@link TokenKeyManager} 获取签名密钥，并使用 ECDSA 对 JWT 进行签名。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * GenerateTokenDto dto = new GenerateTokenDto();
 * dto.setGrantType("authorization_code");
 * dto.setCode("authCode123");
 * String jwt = tokenService.generateToken(dto);
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Slf4j
@Service
public class TokenService {

    /**
     * JSON 编解码工具
     */
    private static final JsonCodec jsonCodec = JsonCodecFactory.instance();

    /**
     * 通用 Redis 工具，用于获取授权码等缓存信息
     */
    private final GenericRedisHelper genericRedisHelper;

    /**
     * Token 密钥管理器，用于获取签名密钥
     */
    private final TokenKeyManager tokenKeyManager;

    @Resource
    private LoginTokenClient loginTokenClient;

    @Resource
    private ApplicationClient applicationClient;

    @Resource
    private SessionService sessionService;

    /**
     * 与 {@link com.g2rain.iam.config.RedisConfig#redisTemplate} 一致，用于授权码原子消费（Lua GET+DEL）。
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 原子读取并删除授权码对应的 Redis 值（Lua 内 GET 后 DEL，避免重放）。
     * 脚本返回类型为 {@link Object}：{@link RedisTemplate} 的 JSON value 序列化器会把 GET 到的 JSON 反序列化为
     * {@link AuthorizationCodeDto}，而非原始 {@link String}（与 {@code setResultType} 声明无关）。
     */
    private static final DefaultRedisScript<Object> AUTH_CODE_CONSUME_SCRIPT;

    static {
        DefaultRedisScript<Object> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local v = redis.call('GET', KEYS[1])\n"
                + "if v == false then return nil end\n"
                + "redis.call('DEL', KEYS[1])\n"
                + "return v"
        );
        script.setResultType(Object.class);
        AUTH_CODE_CONSUME_SCRIPT = script;
    }

    /**
     * 构造方法，注入所需依赖
     *
     * @param genericRedisHelper Redis 工具
     * @param tokenKeyManager    JWT 密钥管理器
     */
    public TokenService(GenericRedisHelper genericRedisHelper, TokenKeyManager tokenKeyManager) {
        this.genericRedisHelper = genericRedisHelper;
        this.tokenKeyManager = tokenKeyManager;
    }

    /**
     * 根据 {@link GenerateTokenDto} 生成 JWT 令牌。
     * <p>
     * 根据不同的 {@link TokenGrantType} 生成对应类型的令牌：
     * <ul>
     *     <li>AUTHORIZATION_CODE：根据授权码生成令牌</li>
     *     <li>EXCHANGE_TOKEN：交换令牌模式</li>
     *     <li>REFRESH_TOKEN：刷新令牌模式生成令牌</li>
     * </ul>
     * </p>
     *
     * @param clientDPoP      客户端级 DPoP
     * @param applicationDPoP 应用级 DPoP
     * @param authorization   token
     * @param dto             生成令牌所需的参数
     * @return {@link TokenVo} 生成的 JWT 令牌
     * @throws BusinessException 当授权码无效或签名失败时抛出
     */
    public TokenVo generateToken(String clientDPoP, String applicationDPoP, String authorization, GenerateTokenDto dto) {
        return switch (TokenGrantType.fromCode(dto.getGrantType())) {
            case AUTHORIZATION_CODE -> code2token(clientDPoP, applicationDPoP, dto.getCode());
            case REFRESH_TOKEN -> refreshToken(clientDPoP, authorization);
            case EXCHANGE_TOKEN -> exchangeToken(clientDPoP, authorization, dto.getUserId());
        };
    }

    /**
     * code 换取 token
     *
     * @param clientDPoP      客户端 DPoP
     * @param applicationDPoP 应用 DPoP
     * @param code            临时 code
     * @return token
     */
    private TokenVo code2token(String clientDPoP, String applicationDPoP, String code) {
        // 校验客户端 DPoP Proof
        String clientId;
        String applicationCode;
        String publicKeyString;
        try {
            SignedJWT signedJWT = SignedJWT.parse(clientDPoP);
            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!Constants.DPoP_HEADER_TYPE.equalsIgnoreCase(header.getType().toString())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof typ");
            }

            // 校验 JWK 类型
            JWK jwk = header.getJWK();
            if (!(jwk instanceof ECKey ecKey)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWK");
            }

            // 校验签名
            JWSVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
            }

            // 获取公钥
            publicKeyString = jwk.toJSONString();

            // 获取客户端标识
            clientId = header.getKeyID();
            if (Strings.isBlank(clientId)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof kid");
            }

            // 获取客户端应用编码
            applicationCode = signedJWT.getJWTClaimsSet().getStringClaim("acd");
            if (Strings.isBlank(applicationCode)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application Code");
            }
        } catch (ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof");
        } catch (JOSEException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
        }

        // 原子消费授权码（GET+DEL），防止同一 code 多次换 token
        AuthorizationCodeDto codeDto = consumeAuthorizationByCode(code);
        String boundClientId = Strings.isBlank(codeDto.getClientId()) ? null : codeDto.getClientId().trim();
        if (Strings.isBlank(boundClientId) || !boundClientId.equals(clientId.trim())) {
            throw new BusinessException(IamErrorCode.OAUTH_AUTHORIZATION_CODE_CLIENT_MISMATCH);
        }
        Long userId = IamUtils.parseLongSafe(codeDto.getUserId(), "userId");

        // 校验 应用 DPoP Proof
        Result<PublicKeyDescriptorVo> applicationResult = applicationClient.getPublicKeyDescriptor(applicationCode);
        if (!applicationResult.isSuccess()) {
            throw ExceptionConverter.of(applicationResult);
        }

        validateApplicationDPoP(applicationDPoP, applicationResult.getData());

        SessionDto session = sessionService.getSession(codeDto.getSessionId());

        if (Objects.isNull(session)) {
            throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "请先登录");
        }

        Long sessionPassportId = IamUtils.parseLongSafe(session.getPassportId(), "passportId");

        Result<TokenJWTPayload> result = loginTokenClient.fetchTokenContext(
            sessionPassportId,
            userId,
            applicationCode,
            codeDto.getThirdPartyIdpLogin(),
            codeDto.getIdpType(),
            codeDto.getIdpSubject(),
            codeDto.getIdpApplicationCode()
        );
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }

        TokenJWTPayload payload = result.getData();
        payload.setPassportId(sessionPassportId);
        // 如果是账号身份, 设置一下账号名称
        if (SessionType.PASSPORT.equals(payload.getSessionType())) {
            payload.setName(session.getName());
        }

        payload.setClientId(clientId);
        payload.setClientPublicKey(publicKeyString);
        return doGenerateToken(applicationCode, payload);
    }

    /**
     * 刷新 Token
     *
     * @param authorization token
     * @return 新 Token
     */
    private TokenVo refreshToken(String clientDPoP, String authorization) {
        if (Strings.isBlank(authorization)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "Authorization");
        }

        // 校验客户端 DPoP Proof
        String applicationCode;
        try {
            SignedJWT signedJWT = SignedJWT.parse(clientDPoP);
            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!Constants.DPoP_HEADER_TYPE.equalsIgnoreCase(header.getType().toString())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof typ");
            }

            // 校验 JWK 类型
            JWK jwk = header.getJWK();
            if (!(jwk instanceof ECKey ecKey)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWK");
            }

            // 校验签名
            JWSVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
            }

            // 获取客户端应用编码
            applicationCode = signedJWT.getJWTClaimsSet().getStringClaim("acd");
            if (Strings.isBlank(applicationCode)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application Code");
            }
        } catch (ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof");
        } catch (JOSEException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
        }

        try {
            // 去掉 "Bearer "
            if (Strings.startsWith(authorization, "Bearer ")) {
                authorization = authorization.substring(7);
            }

            SignedJWT signedJWT = SignedJWT.parse(authorization);
            JWSHeader header = signedJWT.getHeader();
            ECKey publicKey = tokenKeyManager.getKey(header.getKeyID());
            if (Objects.isNull(publicKey)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token keyId");
            }

            JWSVerifier verifier = new ECDSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token");
            }

            String payload = signedJWT.getJWTClaimsSet().toString();
            TokenJWTPayload body = jsonCodec.str2obj(payload, TokenJWTPayload.class);
            Long refreshExpireAt = body.getRefreshExpireAt();

            // 过期
            if (Objects.isNull(refreshExpireAt) || refreshExpireAt < Instant.now().getEpochSecond()) {
                throw new BusinessException(IamErrorCode.REFRESH_TOKEN_EXPIRED);
            }

            ApplicationSelectDto selectDto = new ApplicationSelectDto();
            selectDto.setApplicationCode(applicationCode);
            Result<List<ApplicationVo>> result = applicationClient.selectList(selectDto);
            if (!result.isSuccess()) {
                throw ExceptionConverter.of(result);
            }

            if (Collections.isEmpty(result.getData())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token");
            }

            ApplicationVo application = result.getData().getFirst();

            // 设置新的过期时间
            Instant now = Instant.now();
            body.setIssuedAt(now.getEpochSecond());

            body.setExpireAt(now.plus(Duration.ofSeconds(
                application.getAccessTokenExpiresIn()
            )).getEpochSecond());

            body.setRefreshExpireAt(now.plus(Duration.ofSeconds(
                application.getRefreshTokenExpiresIn()
            )).getEpochSecond());

            return doGenerateToken(applicationCode, body);
        } catch (JOSEException | ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token");
        }
    }

    /**
     * 切换用户身份
     *
     * @param clientDPoP    客户端 DPoP
     * @param authorization token
     * @param userId        用户 ID
     * @return token
     */
    private TokenVo exchangeToken(String clientDPoP, String authorization, Long userId) {
        if (Objects.isNull(userId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "userId");
        }

        if (Strings.isBlank(authorization)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "Authorization");
        }

        try {
            // 校验客户端 DPoP Proof
            SignedJWT signedJWT = SignedJWT.parse(clientDPoP);
            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!Constants.DPoP_HEADER_TYPE.equalsIgnoreCase(header.getType().toString())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof typ");
            }

            // 校验 JWK 类型
            JWK jwk = header.getJWK();
            if (!(jwk instanceof ECKey ecKey)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWK");
            }

            // 校验签名
            JWSVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
            }

            // 获取公钥
            String publicKeyString = jwk.toJSONString();

            // 获取客户端标识
            String clientId = header.getKeyID();
            if (Strings.isBlank(clientId)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof kid");
            }

            // 获取客户端应用编码
            String applicationCode = signedJWT.getJWTClaimsSet().getStringClaim("acd");
            if (Strings.isBlank(applicationCode)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application Code");
            }

            // 去掉 "Bearer "
            if (Strings.startsWith(authorization, "Bearer ")) {
                authorization = authorization.substring(7);
            }

            signedJWT = SignedJWT.parse(authorization);
            header = signedJWT.getHeader();
            ECKey publicKey = tokenKeyManager.getKey(header.getKeyID());
            if (Objects.isNull(publicKey)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token keyId");
            }

            verifier = new ECDSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "token");
            }

            String payloadStr = signedJWT.getJWTClaimsSet().toString();
            TokenJWTPayload body = jsonCodec.str2obj(payloadStr, TokenJWTPayload.class);
            Long refreshExpireAt = body.getRefreshExpireAt();

            // 过期
            if (Objects.isNull(refreshExpireAt) || refreshExpireAt < Instant.now().getEpochSecond()) {
                throw new BusinessException(IamErrorCode.REFRESH_TOKEN_EXPIRED);
            }

            Result<TokenJWTPayload> result = loginTokenClient.fetchTokenContext(
                null, userId, applicationCode, null, null, null, null);
            if (!result.isSuccess()) {
                throw ExceptionConverter.of(result);
            }

            TokenJWTPayload payload = result.getData();
            payload.setPassportId(body.getPassportId());
            payload.setClientId(clientId);
            payload.setClientPublicKey(publicKeyString);
            return doGenerateToken(applicationCode, payload);
        } catch (ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof");
        } catch (JOSEException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof JWS");
        }
    }

    /**
     * 使用当前激活的密钥生成 JWT，并进行签名。
     *
     * @param applicationCode 应用编码
     * @param payload         JWT Payload 中的自定义声明，可以为 {@code null}
     * @return {@link TokenVo} 生成并签名后的 Token
     * @throws BusinessException 当未找到有效密钥或生成 JWT 失败时抛出
     */
    private TokenVo doGenerateToken(String applicationCode, TokenJWTPayload payload) {
        Map<String, Object> payloadClaims = jsonCodec.obj2map(payload);
        ECKey ecKey = tokenKeyManager.getActiveKey();
        if (Objects.isNull(ecKey)) {
            throw new BusinessException(SystemErrorCode.JWT_KEY_PAIR_NON_EXIST);
        }

        try {
            // 获取密钥对标识
            String keyID = ecKey.getKeyID();

            // 构建 JWT Claims
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
            if (Collections.isNotEmpty(payloadClaims)) {
                payloadClaims.forEach(claimsBuilder::claim);
            }

            // 创建 Signed JWT
            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(ESAlgorithm.getAlgorithmFromECKey(ecKey))
                    .type(JOSEObjectType.JWT)
                    .keyID(keyID)
                    .build(),
                claimsBuilder.build()
            );

            // 签名
            signedJWT.sign(new ECDSASigner(ecKey.toECPrivateKey()));

            // 生成 Token
            TokenVo token = new TokenVo(signedJWT.serialize(), keyID);

            // 保存生成 Token 的日志记录
            saveLoginToken(applicationCode, payload);
            // 返回 Token
            return token;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BusinessException(SystemErrorCode.GENERATE_JWT_ERROR);
        }
    }

    /**
     * 保存登陆日志
     *
     * @param applicationCode 应用编码
     * @param payload         token JWT 载荷
     */
    private void saveLoginToken(String applicationCode, TokenJWTPayload payload) {
        LoginTokenDto loginToken = new LoginTokenDto();
        loginToken.setClientId(payload.getClientId());
        if (Objects.nonNull(payload.getSessionType())) {
            loginToken.setSessionType(payload.getSessionType().name());
        }

        loginToken.setOrganId(payload.getOrganId());
        if (Objects.nonNull(payload.getOrganType())) {
            loginToken.setOrganType(payload.getOrganType().name());
        }

        loginToken.setAdminCompany(payload.isAdminCompany());
        loginToken.setPassportId(payload.getPassportId());
        loginToken.setUserId(payload.getUserId());
        loginToken.setRealName(payload.getName());
        loginToken.setAdminUser(payload.isAdminUser());
        loginTokenClient.save(applicationCode, loginToken);
    }

    /**
     * 原子读取并删除授权码元数据（Lua 脚本内 GET 后 DEL），与发码时使用的 Redis 键、JSON 序列化约定一致。
     *
     * @param code 客户端提交的授权码
     * @return 已消费的 {@link AuthorizationCodeDto}
     * @throws BusinessException code 为空、Redis 中不存在或 JSON 无法解析
     */
    private AuthorizationCodeDto consumeAuthorizationByCode(String code) {
        if (Strings.isBlank(code)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "code");
        }
        String redisKey = RedisKeyRule.AUTHORIZATION_CODE.format(code);
        Object raw = redisTemplate.execute(AUTH_CODE_CONSUME_SCRIPT, List.of(redisKey));
        if (raw == null) {
            throw new BusinessException(SystemErrorCode.AUTH_CODE_INVALID, code);
        }
        if (raw instanceof AuthorizationCodeDto dto) {
            return dto;
        }
        if (raw instanceof String s) {
            if (Strings.isBlank(s)) {
                throw new BusinessException(SystemErrorCode.AUTH_CODE_INVALID, code);
            }
            try {
                return jsonCodec.str2obj(s, AuthorizationCodeDto.class);
            } catch (RuntimeException e) {
                log.warn("authorization code payload deserialize failed after consume, key={}", redisKey, e);
                throw new BusinessException(SystemErrorCode.AUTH_CODE_INVALID, code);
            }
        }
        log.warn(
            "authorization code redis value unexpected type {} after consume, key={}",
            raw.getClass().getName(),
            redisKey
        );
        throw new BusinessException(SystemErrorCode.AUTH_CODE_INVALID, code);
    }

    /**
     * 校验应用程序的 DPoP (Demonstrating Proof-of-Possession) 证明。
     * <p>基于 [RFC 9449](https://datatracker.ietf.org) 标准，验证 JWT 的 Header 类型、
     * JWK 格式以及 [Nimbus JOSE + JWT](https://connect2id.com) 签名的合法性。</p>
     *
     * @param applicationDPoP 客户端请求头中的 DPoP 证明字符串 (JWS)
     * @throws BusinessException 当 DPoP 缺失、格式解析错误、密钥类型不符（非 ECKey）或签名验证失败时
     */
    private void validateApplicationDPoP(String applicationDPoP, PublicKeyDescriptorVo descriptor) {
        if (Strings.isBlank(applicationDPoP)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "application-DPoP");
        }

        if (Collections.isEmpty(descriptor.getPublicKey())) {
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "未上传公钥");
        }

        try {
            // noinspection ConstantConditions
            SignedJWT signedJWT = SignedJWT.parse(applicationDPoP);
            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!Constants.DPoP_HEADER_TYPE.equalsIgnoreCase(header.getType().toString())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application DPoP Proof typ");
            }

            // 校验签名
            X509EncodedKeySpec spec = new X509EncodedKeySpec(IamUtils.toDer(descriptor.getPublicKey()));
            JWSVerifier verifier = new ECDSAVerifier((ECPublicKey) KeyFactory.getInstance(descriptor.getPublicKeyAlgorithm()).generatePublic(spec));

            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application DPoP Proof JWS");
            }
        } catch (ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application DPoP Proof");
        } catch (JOSEException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Application DPoP Proof JWS");
        }
    }

    /**
     * 校验 Bearer access token 并返回 JWT 载荷（用于通行证绑定等已登录场景）
     *
     * @param authorization Authorization 头（可含 Bearer 前缀）
     * @return 已校验的 token 载荷
     */
    public TokenJWTPayload requireValidAccessToken(String authorization) {
        if (Strings.isBlank(authorization)) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_UNAUTHORIZED);
        }
        try {
            if (Strings.startsWith(authorization, "Bearer ")) {
                authorization = authorization.substring(7);
            }
            SignedJWT signedJWT = SignedJWT.parse(authorization);
            JWSHeader header = signedJWT.getHeader();
            ECKey publicKey = tokenKeyManager.getKey(header.getKeyID());
            if (Objects.isNull(publicKey)) {
                throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_UNAUTHORIZED);
            }
            JWSVerifier verifier = new ECDSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_UNAUTHORIZED);
            }
            TokenJWTPayload body = jsonCodec.str2obj(signedJWT.getJWTClaimsSet().toString(), TokenJWTPayload.class);
            Long expireAt = body.getExpireAt();
            if (Objects.isNull(expireAt) || expireAt < Instant.now().getEpochSecond()) {
                throw new BusinessException(IamErrorCode.REFRESH_TOKEN_EXPIRED);
            }
            if (body.getPassportId() == null || body.getPassportId() <= 0L) {
                throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_CONTEXT_INVALID);
            }
            if (body.getOrganId() == null || body.getOrganId() <= 0L) {
                throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_CONTEXT_INVALID);
            }
            return body;
        } catch (ParseException | JOSEException e) {
            throw new BusinessException(IamErrorCode.DINGTALK_PASSPORT_BIND_UNAUTHORIZED);
        }
    }
}
