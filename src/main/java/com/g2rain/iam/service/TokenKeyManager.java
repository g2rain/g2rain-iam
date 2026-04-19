package com.g2rain.iam.service;


import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Collections;
import com.g2rain.iam.config.TokenKeyProperties;
import com.g2rain.iam.enums.ESAlgorithm;
import com.g2rain.iam.utils.IamUtils;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * TokenKeyManager 是一个 Spring Bean，用于管理 EC 密钥对的加载、存储以及激活密钥的设置。
 * <p>
 * 该类在应用启动时从 {@link TokenKeyProperties} 中加载 PEM 格式的密钥，并将其存入内存中。
 * 支持：
 * <ul>
 *     <li>根据 keyId 获取指定密钥</li>
 *     <li>获取当前激活的密钥</li>
 *     <li>监听 Nacos 配置变更，只刷新特定 DataId 的密钥配置</li>
 * </ul>
 * </p>
 * <p>
 * 刷新逻辑保证线程安全，通过构建新 Map 并原子替换，避免在刷新期间读取到不完整数据。
 * </p>
 *
 * @author alpha
 * @since 2025/10/12
 */
@Slf4j
@Component
@RefreshScope
public class TokenKeyManager {

    /**
     * 存储 EC 密钥的映射表，其中 key 为密钥的 ID，value 为对应的 ECKey 对象。
     * 用于快速查询指定 keyId 的密钥。
     */
    private Map<String, ECKey> keys = new HashMap<>();

    /**
     * 注入的 TokenKeyProperties，包含所有密钥配置。
     */
    private final TokenKeyProperties properties;

    /**
     * 当前激活的密钥 keyId。
     */
    private String activeKeyId;

    /**
     * 注入的 NacosConfigManager，用于注册配置监听器。
     */
    private final NacosConfigManager nacosConfigManager;

    private static final String DATA_ID = "g2rain-token-keypair.yml";
    private static final String GROUP = "g2rain";

    /**
     * 构造函数，注入 TokenKeyProperties 和 NacosConfigManager。
     *
     * @param properties         TokenKey 配置属性
     * @param nacosConfigManager Nacos 配置管理器
     */
    public TokenKeyManager(TokenKeyProperties properties, NacosConfigManager nacosConfigManager) {
        this.properties = properties;
        this.nacosConfigManager = nacosConfigManager;
    }

    /**
     * 初始化方法，在 Spring 完成 Bean 注入后执行。
     * <p>
     * 执行逻辑：
     * <ol>
     *     <li>初次加载配置中的所有密钥</li>
     *     <li>注册 Nacos 配置监听器，只监听 {@link #DATA_ID}</li>
     *     <li>配置变更时重新加载密钥</li>
     * </ol>
     * </p>
     *
     * @throws BusinessException 如果初始化或注册监听器失败
     */
    @PostConstruct
    public void init() {
        // 初始加载
        reloadKeys();

        try {
            // 注册 Nacos 配置变更监听器，只监听 DATA_ID
            ConfigService configService = nacosConfigManager.getConfigService();
            configService.addListener(DATA_ID, GROUP, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("TokenKey配置变更，重新加载密钥...");
                    reloadKeys();
                }

                @Override
                public Executor getExecutor() {
                    return null; // 使用默认线程池
                }
            });
        } catch (NacosException e) {
            // 如果密钥加载失败，则抛出业务异常，提示初始化 PEM 错误
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, null, new String[]{"初始化Token公私钥"}, null, e);
        }
    }

    /**
     * 刷新密钥配置，构建新 Map 并原子替换，保证线程安全。
     * <p>
     * 遍历 {@link TokenKeyProperties} 中配置的每个密钥，加载 ECKey，并更新当前激活密钥。
     * </p>
     *
     * @throws BusinessException 如果密钥加载失败
     */
    private synchronized void reloadKeys() {
        try {
            Map<String, ECKey> newKeys = new HashMap<>();
            String newActiveKeyId = null;

            for (TokenKeyProperties.KeyConfig config : properties.getKeys()) {
                // 获取算法配置对应的曲线类型（Curve）
                Curve curve = ESAlgorithm.valueOf(config.getAlgorithm()).getCurve();

                // 使用配置信息加载 ECKey 对象
                ECKey ecKey = IamUtils.loadECKey(
                    config.getKeyId(),
                    config.getPublicKey(),
                    config.getPrivateKey(),
                    curve
                );

                // 将加载的密钥存入 Map 中，key 为密钥的 ID
                newKeys.put(config.getKeyId(), ecKey);

                // 如果配置了 active 标记，则将该密钥设置为当前激活密钥
                if (Boolean.TRUE.equals(config.getActive())) {
                    newActiveKeyId = config.getKeyId();
                }
            }

            // 如果没有配置 active 标记，则默认选择第一个密钥作为激活密钥
            if (Objects.isNull(newActiveKeyId) && Collections.isNotEmpty(newKeys)) {
                newActiveKeyId = newKeys.keySet().iterator().next();
            }

            // 原子替换
            this.keys = newKeys;
            this.activeKeyId = newActiveKeyId;
            log.info("TokenKeyManager 初始化完成，当前激活keyId={}", activeKeyId);
        } catch (Exception e) {
            // 如果密钥加载失败，则抛出业务异常，提示初始化 PEM 错误
            throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "初始化PEM错误");
        }
    }

    /**
     * 获取指定 keyId 对应的 ECKey 对象。
     *
     * @param keyId 密钥的 ID。
     * @return 对应的 ECKey 对象，如果未找到则返回 null。
     */
    public ECKey getKey(String keyId) {
        return keys.get(keyId);
    }

    /**
     * 获取当前激活的 ECKey 对象。
     *
     * @return 当前激活的 ECKey 对象。
     * @throws IllegalStateException 如果没有设置激活的密钥，抛出异常。
     */
    public ECKey getActiveKey() {
        return keys.get(activeKeyId);
    }
}
