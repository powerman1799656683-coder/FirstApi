package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AnnouncementItem;
import com.firstapi.backend.service.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/announcements")
public class MyAnnouncementController {

    private final AnnouncementService service;

    public MyAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AnnouncementItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.listForUser(CurrentSessionHolder.require(), keyword));
    }
}
