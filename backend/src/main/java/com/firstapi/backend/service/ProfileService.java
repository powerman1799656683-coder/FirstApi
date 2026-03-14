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
            profile.username = ValidationSupport.requireNotBlank(request.username, "Username is required");
            authUserRepository.updateDisplayName(user.getId(), profile.username);
        }
        if (request.phone != null) {
            profile.phone = request.phone;
        }
        if (request.bio != null) {
            profile.bio = request.bio;
        }
        save(user.getId(), profile);
        return getProfile();
    }

    public ActionResult changePassword(PasswordRequest request) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        AuthUser authUser = authUserRepository.findById(user.getId());
        if (authUser == null) {
            throw new IllegalStateException("Authenticated user no longer exists");
        }

        String oldPassword = ValidationSupport.requireNotBlank(request.oldPassword, "Current password is required");
        String newPassword = ValidationSupport.requireNotBlank(request.newPassword, "New password is required");
        if (!PasswordHashSupport.matches(oldPassword, authUser.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        authUserRepository.updatePasswordHash(user.getId(), PasswordHashSupport.hash(newPassword));
        return new ActionResult("Password updated");
    }

    public synchronized ActionResult enable2fa() {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ProfileData profile = getProfile();
        profile.twoFactorEnabled = true;
        save(user.getId(), profile);
        return new ActionResult("2FA enabled");
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
        profile.role = "ADMIN".equalsIgnoreCase(user.getRole()) ? "Administrator" : "Member";
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
        profile.role = "ADMIN".equalsIgnoreCase(user.getRole()) ? "Administrator" : "Member";
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
            throw new IllegalStateException("Authenticated user no longer exists");
        }
        return user;
    }
}
