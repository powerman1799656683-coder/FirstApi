package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.ProfileData;
import com.firstapi.backend.model.ProfileData.ActionResult;
import com.firstapi.backend.model.ProfileData.PasswordRequest;
import com.firstapi.backend.model.ProfileData.UpdateRequest;
import com.firstapi.backend.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ProfileData> get() {
        return ApiResponse.ok(service.getProfile());
    }

    @PutMapping
    public ApiResponse<ProfileData> update(@RequestBody UpdateRequest request) {
        return ApiResponse.ok("更新成功", service.updateProfile(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<ActionResult> changePassword(@RequestBody PasswordRequest request) {
        return ApiResponse.ok(service.changePassword(request));
    }

    @PostMapping("/enable-2fa")
    public ApiResponse<ActionResult> enable2fa() {
        return ApiResponse.ok(service.enable2fa());
    }
}
