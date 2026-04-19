package com.g2rain.iam.enums;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import lombok.Getter;

/**
 * 支持的椭圆曲线签名算法枚举类，包含 ES256、ES384 和 ES512。
 * <p>
 * 每个算法对应一个椭圆曲线（Curve）和一个 JWS 算法（JWSAlgorithm）。
 * </p>
 * <p>
 * 此枚举类用于指定基于椭圆曲线的签名算法，并提供根据曲线获取算法和从密钥获取算法的功能。
 * </p>
 *
 * @author alpha
 * @since 2025/10/12
 */
@Getter
public enum ESAlgorithm {

    /**
     * 使用 P-256 曲线的 ES256 签名算法。
     */
    ES256(Curve.P_256, JWSAlgorithm.ES256),

    /**
     * 使用 P-384 曲线的 ES384 签名算法。
     */
    ES384(Curve.P_384, JWSAlgorithm.ES384),

    /**
     * 使用 P-521 曲线的 ES512 签名算法。
     */
    ES512(Curve.P_521, JWSAlgorithm.ES512);

    /**
     * 椭圆曲线，表示用于生成签名的算法曲线。
     */
    private final Curve curve;

    /**
     * JWS（JSON Web Signature）算法，表示具体的签名算法。
     */
    private final JWSAlgorithm jwsAlgorithm;

    /**
     * 构造方法，初始化算法对应的椭圆曲线和 JWS 算法。
     *
     * @param curve        椭圆曲线
     * @param jwsAlgorithm 对应的 JWS 签名算法
     */
    ESAlgorithm(Curve curve, JWSAlgorithm jwsAlgorithm) {
        this.curve = curve;
        this.jwsAlgorithm = jwsAlgorithm;
    }

    /**
     * 根据椭圆曲线返回对应的签名算法。
     *
     * @param curve 椭圆曲线
     * @return {@link ESAlgorithm}，对应的签名算法
     * @throws BusinessException 如果找不到对应的签名算法
     */
    public static ESAlgorithm fromCurve(Curve curve) {
        for (ESAlgorithm alg : values()) {
            if (alg.getCurve().equals(curve)) {
                return alg;
            }
        }

        throw new BusinessException(
            SystemErrorCode.RESOURCE_NOT_FOUND,
            curve.getName()
        );
    }

    /**
     * 根据 EC 密钥获取对应的 JWS 签名算法。
     *
     * @param ecKey EC 密钥
     * @return {@link JWSAlgorithm}，对应的 JWS 签名算法
     * @throws BusinessException 如果找不到对应的签名算法
     */
    public static JWSAlgorithm getAlgorithmFromECKey(ECKey ecKey) {
        return fromCurve(ecKey.getCurve()).getJwsAlgorithm();
    }
}
