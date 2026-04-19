package com.g2rain.iam.service;


import com.g2rain.basis.dto.PassportDto;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.PassportClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Passport 相关业务服务。
 *
 * <p>封装对 {@link PassportClient} 的调用，用于账号注册等操作。</p>
 */
@Service
public class PassportService {

    @Resource
    private PassportClient passportClient;

    /**
     * 注册账号，底层通过 {@link PassportClient} 调用 g2rain-basis 的保存接口。
     *
     * @param passportDto 注册信息
     * @return 统一结果
     */
    public Result<?> register(PassportDto passportDto) {
        return passportClient.save(passportDto);
    }
}

