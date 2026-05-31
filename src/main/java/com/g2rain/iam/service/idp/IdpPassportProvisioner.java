package com.g2rain.iam.service.idp;


import com.g2rain.basis.dto.PassportDto;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.service.PassportService;
import com.g2rain.iam.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IdP 登录场景下自动注册 passport 的通用逻辑。
 */
@Component
@RequiredArgsConstructor
public class IdpPassportProvisioner {

    private static final int REAL_NAME_MAX_LENGTH = 128;

    private final PassportService passportService;

    /**
     * @param username            平台账号名（由具体 IdP 生成）
     * @param displayName         IdP 展示名，可空
     * @param defaultDisplayName  展示名为空时的默认 realName
     * @return 注册结果，成功时 data 为新 passportId
     */
    public Result<?> registerPassport(String username, String displayName, String defaultDisplayName) {
        PassportDto passportDto = new PassportDto();
        passportDto.setUsername(username);
        passportDto.setPassword(Constants.THIRD_PARTY_IDP_AUTO_REGISTER_PASSPORT_PASSWORD);
        passportDto.setRealName(resolveRealName(displayName, defaultDisplayName));
        passportDto.setPasswordTrusted(false);

        return passportService.register(passportDto);
    }

    private static String resolveRealName(String displayName, String defaultDisplayName) {
        String realName = Strings.isBlank(displayName) ? defaultDisplayName : displayName.trim();
        if (realName.length() > REAL_NAME_MAX_LENGTH) {
            return realName.substring(0, REAL_NAME_MAX_LENGTH);
        }
        return realName;
    }
}
