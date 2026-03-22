package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.model.ProfileData;
import com.firstapi.backend.model.ProfileData.ActionResult;
import com.firstapi.backend.model.ProfileData.PasswordRequest;
import com.firstapi.backend.model.ProfileData.UpdateRequest;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthUserRepository authUserRepository;

    public ProfileService(JdbcTemplate jdbcTemplate, AuthUserRepository authUserRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.authUserRepository = authUserRepository;
    }

    public synchronized ProfileData getProfile() {
        AuthenticatedUser sessionUser = CurrentSessionHolder.require();
        AuthUser identity = currentIdentity(sessionUser);
        List<ProfileData> result = jdbcTemplate.query(
                "select `username`, `email`, `role_name`, `uid_label`, `phone`, `bio`, `verified`, `two_factor_enabled` from `profiles` where `id` = ?",
                (rs, rowNum) -> {
                    ProfileData profile = new ProfileData();
                    profile.username = rs.getString("username");
                    profile.email = rs.getString("email");
                    profile.role = rs.getString("role_name");
                    profile.uid = rs.getString("uid_label");
                    profile.phone = rs.getString("phone");
                    profile.bio = rs.getString("bio");
                    profile.verified = rs.getBoolean("verified");
                    profile.twoFactorEnabled = rs.getBoolean("two_factor_enabled");
                    return profile;
                },
                identity.getId()
        );

        ProfileData profile = result.isEmpty() ? defaultProfile(identity) : result.get(0);
        syncIdentity(profile, identity);
        save(identity.getId(), profile);
        return profile;
    }

    public synchronized ProfileData updateProfile(UpdateRequest request) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ProfileData profile = getProfile();
        if (request.username != null) {
            String username = ValidationSupport.requireNotBlank(request.username, "用户名不能为空");
            if (username.length() > 64) {
                throw new IllegalArgumentException("用户名不能超过 64 个字符");
            }
            if (username.contains("<") || username.contains(">")) {
                throw new IllegalArgumentException("用户名包含非法字符");
            }
            profile.username = username;
            authUserRepository.updateDisplayName(user.getId(), profile.username);
        }
        if (request.phone != null) {
            String phone = request.phone.trim();
            if (!phone.isEmpty() && !phone.matches("^[+]?[0-9\\-\\s]{6,20}$")) {
                throw new IllegalArgumentException("手机号格式无效");
            }
            profile.phone = phone;
        }
        if (request.bio != null) {
            if (request.bio.length() > 500) {
                throw new IllegalArgumentException("个人简介不能超过 500 个字符");
            }
            profile.bio = request.bio;
        }
        save(user.getId(), profile);
        return getProfile();
    }

    public ActionResult changePassword(PasswordRequest request) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        AuthUser authUser = authUserRepository.findById(user.getId());
        if (authUser == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }

        String oldPassword = ValidationSupport.requireNotBlank(request.oldPassword, "当前密码不能为空");
        String newPassword = ValidationSupport.requireNotBlank(request.newPassword, "新密码不能为空");
        if (!PasswordHashSupport.matches(oldPassword, authUser.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }

        authUserRepository.updatePasswordHash(user.getId(), PasswordHashSupport.hash(newPassword));
        return new ActionResult("密码已更新");
    }

    public synchronized ActionResult enable2fa() {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ProfileData profile = getProfile();
        profile.twoFactorEnabled = true;
        save(user.getId(), profile);
        return new ActionResult("双重验证已开启");
    }

    private void save(Long profileId, ProfileData profile) {
        jdbcTemplate.update(
                "insert into `profiles` (`id`, `username`, `email`, `role_name`, `uid_label`, `phone`, `bio`, `verified`, `two_factor_enabled`) values (?, ?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update `username` = values(`username`), `email` = values(`email`), `role_name` = values(`role_name`), `uid_label` = values(`uid_label`), `phone` = values(`phone`), `bio` = values(`bio`), `verified` = values(`verified`), `two_factor_enabled` = values(`two_factor_enabled`)",
                profileId,
                profile.username,
                profile.email,
                profile.role,
                profile.uid,
                profile.phone,
                profile.bio,
                profile.verified,
                profile.twoFactorEnabled
        );
    }

    private ProfileData defaultProfile(AuthUser user) {
        ProfileData profile = new ProfileData();
        profile.username = user.getDisplayName();
        profile.email = user.getEmail();
        profile.role = "ADMIN".equalsIgnoreCase(user.getRole()) ? "管理员" : "用户";
        profile.uid = String.valueOf(user.getId());
        profile.phone = "";
        profile.bio = "";
        profile.verified = true;
        profile.twoFactorEnabled = false;
        return profile;
    }

    private void syncIdentity(ProfileData profile, AuthUser user) {
        profile.username = user.getDisplayName();
        profile.email = user.getEmail();
        profile.role = "ADMIN".equalsIgnoreCase(user.getRole()) ? "管理员" : "用户";
        profile.uid = String.valueOf(user.getId());
        if (profile.verified == null) {
            profile.verified = true;
        }
        if (profile.twoFactorEnabled == null) {
            profile.twoFactorEnabled = false;
        }
    }

    private AuthUser currentIdentity(AuthenticatedUser sessionUser) {
        AuthUser user = authUserRepository.findById(sessionUser.getId());
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        return user;
    }
}
