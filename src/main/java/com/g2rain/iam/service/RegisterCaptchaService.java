package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.vo.CaptchaRegisterVo;
import com.g2rain.common.utils.Strings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 注册数字图片验证码：生成/存储/校验（Redis）。
 *
 * <p>默认策略：</p>
 * <ul>
 *     <li>注册限流：同 IP 10 分钟内最多 5 次</li>
 *     <li>验证码有效期：180 秒</li>
 *     <li>验证码错误：failCount 达到 5 次后删除</li>
 * </ul>
 */
@Service
public class RegisterCaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==== Redis key ====
    private static final String CAPTCHA_REGISTER_KEY_PREFIX = "iam:captcha:register:%s";
    private static final String REGISTER_RL_KEY_PREFIX = "iam:register:rl:%s";

    // ==== Strategy ====
    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(180);
    private static final Duration REGISTER_RL_WINDOW = Duration.ofMinutes(10);
    private static final long REGISTER_RL_MAX = 5;
    private static final int CAPTCHA_FAIL_MAX = 5;

    // hash salt：避免直接存 code 明文
    private static final String CAPTCHA_SALT = "g2rain-iam-captcha-salt";

    private final GenericRedisHelper genericRedisHelper;

    public RegisterCaptchaService(GenericRedisHelper genericRedisHelper) {
        this.genericRedisHelper = genericRedisHelper;
    }

    /**
     * 生成验证码并存储到 Redis。
     */
    public CaptchaRegisterVo generateRegisterCaptcha(HttpServletRequest request) {
        String ip = resolveClientIp(request);

        String captchaId = generateCaptchaId();
        String code = generateDigitsCode(4);

        String hash = sha256Hex(code + ":" + CAPTCHA_SALT);

        genericRedisHelper.set(
            captchaKey(captchaId),
            new CaptchaValue(ip, hash, 0),
            CAPTCHA_TTL
        );

        String imageBase64 = renderDigitsCaptchaBase64(code);
        return new CaptchaRegisterVo(captchaId, imageBase64);
    }

    /**
     * 校验验证码并消费（成功后删除）。
     *
     * @return 返回错误文案（失败时）；成功返回 null。
     */
    public String validateRegisterCaptcha(
        HttpServletRequest request,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
    ) {
        String ip = resolveClientIp(request);
        if (Strings.isBlank(captchaId) || Strings.isBlank(captchaCode)) {
            return "验证码错误或已过期";
        }

        if (!captchaCode.matches("^\\d{4}$")) {
            return "验证码错误或已过期";
        }

        String key = captchaKey(captchaId);
        CaptchaValue value = genericRedisHelper.get(key, CaptchaValue.class);
        if (value == null) {
            return "验证码错误或已过期";
        }

        // 绑定同一 IP，避免 code 被转发重用
        if (!ip.equals(value.ip())) {
            return "验证码错误或已过期";
        }

        String hash = sha256Hex(captchaCode + ":" + CAPTCHA_SALT);
        if (!hash.equals(value.hash())) {
            long nextFail = value.failCount() + 1;

            if (nextFail >= CAPTCHA_FAIL_MAX) {
                genericRedisHelper.delete(key);
            } else {
                Long ttlSeconds = genericRedisHelper.getExpire(key);
                Duration ttl = ttlSeconds == null || ttlSeconds <= 0 ? CAPTCHA_TTL : Duration.ofSeconds(ttlSeconds);
                genericRedisHelper.set(key, new CaptchaValue(ip, value.hash(), nextFail), ttl);
            }
            return "验证码错误或已过期";
        }

        // 成功后消费验证码，防止重放
        genericRedisHelper.delete(key);
        return null;
    }

    /**
     * 注册限流（同 IP）。
     *
     * @return 返回错误文案（失败时）；成功返回 null。
     */
    public String checkRegisterRateLimit(HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String key = registerRlKey(ip);

        // 不存在则初始化，存在则 increment
        if (!Boolean.TRUE.equals(genericRedisHelper.exists(key))) {
            genericRedisHelper.set(key, 1L, REGISTER_RL_WINDOW);
            return null;
        }

        long count = genericRedisHelper.increment(key, 1);
        if (count > REGISTER_RL_MAX) {
            return "请求过于频繁，请稍后再试";
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (Strings.isNotBlank(xForwardedFor)) {
            // 取最左侧（原始发起方）
            String[] parts = xForwardedFor.split(",");
            if (parts.length > 0 && Strings.isNotBlank(parts[0])) {
                return parts[0].trim();
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (Strings.isNotBlank(xRealIp)) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    private String generateCaptchaId() {
        // 足够随机即可，避免使用可预测序列
        return Long.toHexString(RANDOM.nextLong()) + Long.toHexString(RANDOM.nextLong());
    }

    private String captchaKey(String captchaId) {
        return String.format(CAPTCHA_REGISTER_KEY_PREFIX, captchaId);
    }

    private String registerRlKey(String ip) {
        return String.format(REGISTER_RL_KEY_PREFIX, ip);
    }

    private String generateDigitsCode(int digits) {
        int max = (int) Math.pow(10, digits);
        int min = (int) Math.pow(10, digits - 1);
        int value = RANDOM.nextInt(max - min) + min;
        return String.valueOf(value);
    }

    private String renderDigitsCaptchaBase64(String code) {
        try {
            int width = 120;
            int height = 44;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = image.createGraphics();
            g.setColor(randomLightColor());
            g.fillRect(0, 0, width, height);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.setColor(randomDarkColor());
            FontMetrics fm = g.getFontMetrics();
            int x = (width - fm.stringWidth(code)) / 2;
            int y = (height - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(code, x, y);

            // 噪声线
            for (int i = 0; i < 6; i++) {
                g.setColor(randomDarkColor());
                int x1 = RANDOM.nextInt(width);
                int y1 = RANDOM.nextInt(height);
                int x2 = RANDOM.nextInt(width);
                int y2 = RANDOM.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            // 图片生成失败视为内部错误
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "生成验证码失败", e);
        }
    }

    private Color randomLightColor() {
        int r = 200 + RANDOM.nextInt(55);
        int g = 200 + RANDOM.nextInt(55);
        int b = 200 + RANDOM.nextInt(55);
        return new Color(r, g, b);
    }

    private Color randomDarkColor() {
        int r = 20 + RANDOM.nextInt(80);
        int g = 20 + RANDOM.nextInt(80);
        int b = 20 + RANDOM.nextInt(80);
        return new Color(r, g, b);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // HexFormat 在 Java 17+ 可用
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "hash 失败", e);
        }
    }

    /**
     * Redis 内部存储结构。
     */
    private record CaptchaValue(
        String ip,
        String hash,
        long failCount
    ) {
    }
}

