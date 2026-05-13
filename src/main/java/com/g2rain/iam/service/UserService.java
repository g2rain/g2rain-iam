package com.g2rain.iam.service;


import com.g2rain.basis.dto.UserSelectDto;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.UserClient;
import com.g2rain.iam.dto.SessionDto;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户服务，提供与用户相关的业务逻辑。
 * <p>
 * 该服务用于管理用户信息，提供用户列表查询等功能。可以通过传入会话信息来查询用户列表。
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
@Service
public class UserService {

    @Resource
    private UserClient userClient;

    /**
     * 获取用户列表。
     * <p>
     * 该方法返回一个模拟的用户列表，包含用户的 ID 和用户名。实际应用中，可能会从数据库或外部服务获取真实的用户信息。
     * </p>
     *
     * @param session 当前会话信息，包含用户的相关信息
     * @return {@code List<Map<String, String>>} 模拟的用户列表，每个用户是一个包含 ID 和用户名的 Map
     */
    public List<Map<String, String>> listUsers(SessionDto session) {
        if (Strings.isBlank(session.getPassportId())) {
            return List.of();
        }

        UserSelectDto selectDto = new UserSelectDto();
        selectDto.setPassportId(Long.valueOf(session.getPassportId()));
        Result<List<UserVo>> result = userClient.selectList(selectDto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }

        return result.getData().stream().map(user -> Map.of(
            "id", String.valueOf(user.getId()),
            "username", user.getRealName()
        )).toList();
    }

    /**
     * 当前通行证下的用户完整列表（与 {@link #listUsers(SessionDto)} 同源查询）。
     */
    public List<UserVo> listUserVos(SessionDto session) {
        if (Strings.isBlank(session.getPassportId())) {
            return List.of();
        }

        UserSelectDto selectDto = new UserSelectDto();
        selectDto.setPassportId(Long.valueOf(session.getPassportId()));
        Result<List<UserVo>> result = userClient.selectList(selectDto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }

        List<UserVo> data = result.getData();
        return data != null ? data : List.of();
    }
}
