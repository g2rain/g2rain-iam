package com.g2rain.iam.utils;


import com.g2rain.basis.enums.BasisErrorCode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.enums.ESAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * IAM 工具类，包含生成令牌、会话 ID、授权码、重定向登录 URL、加载密钥等方法。
 * <p>
 * 本工具类主要用于生成与身份验证（IAM）相关的令牌、会话 ID，以及重定向登录链接等操作。
 * </p>
 * <p>
 * 还提供了用于生成 EC 密钥对的工具方法，可用于身份验证和签名操作。
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
public class IamUtils {
    private static final int DEFAULT_LENGTH = 32; // 32字节 = 256bit
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 用于清理 PEM 文本中非 Base64 内容的正则：
     * <ul>
     *     <li>BEGIN / END 头尾</li>
     *     <li>所有空白字符（换行、空格、制表符等）</li>
     * </ul>
     */
    private static final Pattern PEM_CLEAN_PATTERN = Pattern.compile("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s");

    /**
     * Base64 解码器（线程安全，可复用）
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    /**
     * 私有化构造方法，防止实例化。
     */
    private IamUtils() {
        // 防止实例化
    }

    /**
     * 将字符串安全转换为 Long。
     *
     * <p>规则：</p>
     * <ul>
     *     <li>如果输入为 {@code null} 或空白字符串，返回 {@code null}。</li>
     *     <li>如果字符串只包含数字字符（0-9），返回对应的 {@code Long} 值。</li>
     *     <li>如果字符串非空且包含非数字字符，抛出 {@link BusinessException}，错误码为 {@link SystemErrorCode#PARAM_VAL_INVALID}，并携带字段名信息。</li>
     * </ul>
     *
     * @param str       待转换字符串
     * @param fieldName 字段名称，用于异常提示
     * @return 转换后的 Long 值，或者 {@code null}
     * @throws BusinessException 当字符串非空且不全为数字时抛出
     */
    public static Long parseLongSafe(String str, String fieldName) {
        if (Strings.isBlank(str)) {
            return null;
        }

        if (str.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(str);
        }

        throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, fieldName);
    }

    /**
     * 生成一个随机的令牌，通常用于身份验证或会话标识。
     * <p>
     * 生成的令牌为 Base64 URL 编码，不包含填充字符。
     * </p>
     *
     * @return 随机生成的令牌
     */
    private static String generateToken() {
        byte[] bytes = new byte[DEFAULT_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成一个会话 ID，基于随机令牌生成。
     * <p>
     * 会话 ID 可用于标识用户的会话，通常用于登录会话管理。
     * </p>
     *
     * @return 生成的会话 ID
     */
    public static String generateSessionId() {
        return generateToken();
    }

    /**
     * 生成一个授权码，基于随机令牌生成。
     * <p>
     * 授权码通常用于 OAuth2 等认证流程中，作为授权过程的一部分。
     * </p>
     *
     * @return 生成的授权码
     */
    public static String generateAuthorizationCode() {
        return generateToken();
    }

    /**
     * 构造并返回登录页面的重定向 URL。
     * <p>
     * 该方法会根据客户端 ID、重定向 URI 和状态值生成登录 URL，并进行重定向。
     * </p>
     *
     * @param clientId    客户端 ID
     * @param redirectUri 登录后重定向的 URI
     * @param state       防止 CSRF 攻击的状态参数
     * @return {@link ModelAndView}，包含重定向 URL
     */
    public static ModelAndView redirectLogin(String clientId, String redirectUri, String state) {
        UriComponentsBuilder loginUrl = UriComponentsBuilder.fromPath("/auth/login")
            .queryParam("clientId", clientId)
            .queryParam("redirectUri", redirectUri);

        if (state != null) {
            loginUrl.queryParam("state", state);
        }

        return new ModelAndView(Constants.REDIRECT + loginUrl.build().toUriString());
    }

    /**
     * 判断 Token 绑定的 {@code clientPublicKey} 与 DPoP Proof 头 JWK 是否同一公钥（RFC 7638 Thumbprint）。
     */
    public static boolean matches(String clientPublicKey, JWK proofJwk) {
        if (Strings.isBlank(clientPublicKey) || Objects.isNull(proofJwk)) {
            return false;
        }

        try {
            JWK stored = JWK.parse(clientPublicKey);
            return stored.toPublicJWK().computeThumbprint()
                .equals(proofJwk.toPublicJWK().computeThumbprint());
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    /**
     * 从提供的公钥和私钥字符串加载一个 EC 密钥对（椭圆曲线密钥对）。
     *
     * @param keyId      与此 EC 密钥对关联的密钥 ID。
     * @param publicKey  公钥的 PEM 格式字符串（Base64 编码）。
     * @param privateKey 私钥的 PEM 格式字符串（Base64 编码）。
     * @param curve      要使用的椭圆曲线（例如：P-256，P-384 等）。
     * @return 返回包含公钥和私钥的 ECKey 对象，同时包含密钥 ID。
     * @throws NoSuchAlgorithmException 如果找不到 EC 算法时抛出此异常。
     * @throws InvalidKeySpecException  如果公钥或私钥规格无效时抛出此异常。
     */
    public static ECKey loadECKey(String keyId, String publicKey, String privateKey, Curve curve) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 加载公钥
        ECPublicKey pubKey = loadPublicKey(publicKey);
        // 加载私钥
        ECPrivateKey priKey = loadPrivateKey(privateKey);
        // 返回包含指定椭圆曲线、公钥、私钥和密钥 ID 的 ECKey
        return new ECKey.Builder(curve, pubKey).privateKey(priKey).keyID(keyId).build();
    }

    /**
     * 从 PEM 编码格式的字符串加载椭圆曲线公钥。
     * PEM 字符串格式应为：
     * <pre>
     * -----BEGIN PUBLIC KEY-----
     * (Base64 编码的公钥内容)
     * -----END PUBLIC KEY-----
     * </pre>
     *
     * @param publicKey 公钥的 PEM 格式字符串。
     * @return 返回一个 ECPublicKey 对象。
     * @throws NoSuchAlgorithmException 如果无法获取 EC 算法时抛出此异常。
     * @throws InvalidKeySpecException  如果公钥规格无效时抛出此异常。
     */
    private static ECPublicKey loadPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 根据解码后的字节数组生成公钥
        X509EncodedKeySpec spec = new X509EncodedKeySpec(toDer(publicKey));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
    }

    /**
     * 从 PEM 编码格式的字符串加载椭圆曲线私钥。
     * PEM 字符串格式应为：
     * <pre>
     * -----BEGIN PRIVATE KEY-----
     * (Base64 编码的私钥内容)
     * -----END PRIVATE KEY-----
     * </pre>
     *
     * @param privateKey 私钥的 PEM 格式字符串。
     * @return 返回一个 ECPrivateKey 对象。
     * @throws NoSuchAlgorithmException 如果无法获取 EC 算法时抛出此异常。
     * @throws InvalidKeySpecException  如果私钥规格无效时抛出此异常。
     */
    private static ECPrivateKey loadPrivateKey(String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 根据解码后的字节数组生成私钥
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(toDer(privateKey));
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
    }

    /**
     * 将 PEM 格式公钥转换为 DER 二进制字节。
     *
     * <p>该方法通常用于：</p>
     * <ul>
     *     <li>客户端下载 DER 格式公钥</li>
     *     <li>运行期密钥解析、加密、验签</li>
     * </ul>
     *
     * @param pem PEM 格式公钥字符串
     * @return DER 编码的公钥字节
     * @throws BusinessException 当 PEM 内容非法或 Base64 解码失败时抛出
     */
    public static byte[] toDer(String pem) {
        if (Strings.isBlank(pem)) {
            throw new BusinessException(BasisErrorCode.PUB_KEY_INVALID_KEY);
        }

        try {
            return pemToDerInternal(pem);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BasisErrorCode.PUB_KEY_INVALID_KEY);
        }
    }

    /**
     * 内部方法：从 PEM 文本中提取 Base64 内容并解码为 DER。
     */
    private static byte[] pemToDerInternal(String pem) {
        String base64 = PEM_CLEAN_PATTERN.matcher(pem).replaceAll("");
        return BASE64_DECODER.decode(base64);
    }

    /**
     * 生成 EC 密钥对，并输出 PEM 格式的公私钥和生成的 keyId。
     * <p>
     * 该方法用于在开发过程中生成新的 EC 密钥对，并打印 PEM 格式密钥和 keyId。
     * </p>
     *
     * @param args 命令行参数（目前未使用）
     * @throws NoSuchAlgorithmException           不支持的加密算法
     * @throws InvalidAlgorithmParameterException 无效的算法参数
     */
    @SuppressWarnings("java:S106")
    public static void generateKeyPair(String[] args) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        // 1️⃣ 生成 EC 密钥对
        ESAlgorithm algorithm = ESAlgorithm.ES256;
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(algorithm.getCurve().toECParameterSpec());

        KeyPair keyPair = keyGen.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
        ECPrivateKey pri = (ECPrivateKey) keyPair.getPrivate();

        // 2️⃣ 构造 ECKey 对象（仅公钥用于生成 kid）
        ECKey publicOnly = new ECKey.Builder(algorithm.getCurve(), pub).build();

        // 3️⃣ 生成 keyId
        String pubJson = publicOnly.toPublicJWK().toJSONObject().toString();
        String input = pubJson + "|" + algorithm.name() + "|" + System.currentTimeMillis();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        String keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        // 4️⃣ 输出 keyId
        System.out.println("keyId:\n" + keyId);

        // 5️⃣ 输出 PEM（可保存到配置文件）
        String publicPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pub.getEncoded()) +
            "\n-----END PUBLIC KEY-----";

        String privatePem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pri.getEncoded()) +
            "\n-----END PRIVATE KEY-----";

        System.out.println("Public Key PEM:\n" + publicPem);
        System.out.println("Private Key PEM:\n" + privatePem);
    }
}
