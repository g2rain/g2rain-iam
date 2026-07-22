package com.g2rain.iam.service;


import com.g2rain.basis.dto.UserSelectDto;
import com.g2rain.basis.idp.resolve.vo.IdpPassportResolveVo;
import com.g2rain.basis.idp.resolve.vo.IdpPassportUserVo;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.UserClient;
import com.g2rain.iam.client.UserInternalClient;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.service.idp.IdpBindingSupport;
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

    @Resource
    private UserInternalClient userInternalClient;

    @Resource
    private IdpBindingSupport idpBindingSupport;

    /**
     * 获取用户列表。
     * <p>
     * IdP 登录会话优先走无隔离 resolve 结果，避免 consent 页被 organ 隔离拦截。
     * </p>
     *
     * @param session 当前会话信息，包含用户的相关信息
     * @return 用户列表，每个用户是一个包含 ID 和用户名的 Map
     */
    public List<Map<String, String>> listUsers(SessionDto session) {
        return listUserVos(session).stream().map(user -> Map.of(
            "id", String.valueOf(user.getId()),
            "username", user.getRealName() == null ? "" : user.getRealName()
        )).toList();
    }

    /**
     * 当前通行证下的用户完整列表（与 {@link #listUsers(SessionDto)} 同源查询）。
     */
    public List<UserVo> listUserVos(SessionDto session) {
        if (Strings.isBlank(session.getPassportId()) && !IdpBindingSupport.hasIdpContext(session)) {
            return List.of();
        }

        if (IdpBindingSupport.hasIdpContext(session)) {
            IdpPassportResolveVo resolved = idpBindingSupport.resolve(session);
            if (resolved != null) {
                if (Collections.isNotEmpty(resolved.getUsers())) {
                    return resolved.getUsers().stream().map(this::toUserVo).toList();
                }
                Long passportId = resolved.getPassportId();
                if (passportId != null && passportId > 0L) {
                    return selectUsersWithoutIsolation(passportId);
                }
            }
        }

        if (Strings.isBlank(session.getPassportId())) {
            return List.of();
        }

        return selectUsersWithIsolation(Long.valueOf(session.getPassportId()));
    }

    private List<UserVo> selectUsersWithoutIsolation(Long passportId) {
        UserSelectDto selectDto = new UserSelectDto();
        selectDto.setPassportId(passportId);
        Result<List<UserVo>> result = userInternalClient.selectListWithoutIsolation(selectDto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        List<UserVo> data = result.getData();
        return data != null ? data : List.of();
    }

    private List<UserVo> selectUsersWithIsolation(Long passportId) {
        UserSelectDto selectDto = new UserSelectDto();
        selectDto.setPassportId(passportId);
        Result<List<UserVo>> result = userClient.selectList(selectDto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        List<UserVo> data = result.getData();
        return data != null ? data : List.of();
    }

    private UserVo toUserVo(IdpPassportUserVo user) {
        UserVo vo = new UserVo();
        vo.setId(user.getUserId());
        vo.setOrganId(user.getOrganId());
        vo.setRealName(user.getRealName());
        return vo;
    }
}
