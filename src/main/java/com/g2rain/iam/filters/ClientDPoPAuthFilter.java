package com.g2rain.iam.filters;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.iam.utils.Constants;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author alpha
 * @since 2025/10/5
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 180)
public record ClientDPoPAuthFilter(Tracer tracer) implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        // 下游继续执行
        if (!"/auth/token".equalsIgnoreCase(req.getRequestURI())) {
            String requestId = UUID.randomUUID().toString();
            PrincipalContextHolder.setTraceId(resolveTraceId());
            PrincipalContextHolder.setRequestId(requestId);
            PrincipalContextHolder.setRequestTime(Moments.nowString());
            // 开启 Baggage 作用域（自动写入 MDC）
            try (var _ = tracer.createBaggageInScope(Constants.REQUEST_ID, requestId)) {
                // 下游继续执行
                chain.doFilter(request, response);
            }
            return;
        }

        // 解析 DPoP proof
        String jwt = req.getHeader("DPoP");
        if (Strings.isBlank(jwt)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "Client DPoP");
        }

        // 校验（typ、签名、jti、防重放、htm、htu 等）
        String requestId = verify(req, jwt);

        // 开启 Baggage 作用域（自动写入 MDC）
        try (var _ = tracer.createBaggageInScope(Constants.REQUEST_ID, requestId)) {
            // 下游继续执行
            chain.doFilter(request, response);
        }
    }

    /**
     * 校验 JWT
     * <p>
     * 包括 typ 类型、JWK 类型与签名正确性。
     * </p>
     *
     * @param jwt SignedJWT 字符串
     */
    private String verify(HttpServletRequest request, String jwt) {
        try {
            // noinspection ConstantConditions
            SignedJWT signedJWT = SignedJWT.parse(jwt);

            JWSHeader header = signedJWT.getHeader();

            // 校验 typ
            if (!"dpop+jwt".equalsIgnoreCase(header.getType().toString())) {
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

            // 校验时间戳
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date iat = claims.getIssueTime();
            Instant now = Instant.now();

            // iat 为空
            if (Objects.isNull(iat)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof iat");
            }

            // iat 过期 || 未来
            Instant timestamp = iat.toInstant();
            if (timestamp.isAfter(now.plusSeconds(30)) || timestamp.isBefore(now.minusSeconds(60))) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof iat");
            }

            // 校验 jti
            String jti = claims.getJWTID();
            if (Objects.isNull(jti)) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof jti");
            }

            // 校验 htm
            String htm = claims.getStringClaim("htm");
            if (Strings.isBlank(htm)) {
                throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "Client DPoP Proof htm");
            }

            // 校验请求方法是否匹配
            if (!htm.equalsIgnoreCase(request.getMethod())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof htm");
            }

            // 校验 htu
            String htu = claims.getStringClaim("htu");
            if (Strings.isBlank(htu)) {
                throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "Client DPoP Proof htu");
            }

            if (!htu.equals(request.getRequestURI())) {
                throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof htu");
            }

            PrincipalContextHolder.setRequestId(jti);
            PrincipalContextHolder.setTraceId(resolveTraceId());
            PrincipalContextHolder.setRequestTime(Moments.formatEpochMillis(timestamp.toEpochMilli()));
            return jti;
        } catch (ParseException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client DPoP Proof");
        } catch (JOSEException e) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "Client PoP Proof JWS");
        }
    }

    /**
     * 解析与当前请求一致的链路 traceId，供 {@link PrincipalContextHolder} 与日志 {@code %X{traceId}} 对齐。
     * <p>
     * 优先取自 Micrometer 当前 Span（与入站 {@code traceparent} / 服务端观测一致）；若无当前 Span
     * （例如 Filter 早于观测建立或 tracing 未启用），则退化为无横线 UUID，避免业务侧拿到 {@code null}。
     * </p>
     *
     * @return 非空 trace 标识字符串
     */
    private String resolveTraceId() {
        return Optional.ofNullable(tracer.currentSpan())
            .map(Span::context)
            .map(TraceContext::traceId)
            .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
    }
}
