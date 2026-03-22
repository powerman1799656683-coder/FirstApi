package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AnnouncementItem;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AnnouncementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository repository;

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(repository);
    }

    @Test
    void listForUserOnlyReturnsPublishedAnnouncements() {
        AnnouncementItem published = new AnnouncementItem(1L, "Published", "visible", "notice", "published", "all", "2026/03/19 12:00:00");
        AnnouncementItem draft = new AnnouncementItem(2L, "Draft", "hidden", "notice", "draft", "all", "2026/03/19 12:01:00");
        AnnouncementItem chinesePublished = new AnnouncementItem(3L, "ChinesePublished", "visible", "notice", "发布中", "所有用户", "2026/03/19 12:02:00");
        AnnouncementItem chineseDraft = new AnnouncementItem(4L, "ChineseDraft", "hidden", "notice", "草稿", "所有用户", "2026/03/19 12:03:00");

        when(repository.findAll()).thenReturn(List.of(chineseDraft, chinesePublished, draft, published));

        AuthenticatedUser currentUser = new AuthenticatedUser(8L, "member", "member", "member@example.com", "USER");
        PageResponse<AnnouncementItem> response = service.listForUser(currentUser);

        assertThat(response.getItems()).extracting(AnnouncementItem::getTitle)
                .containsExactly("ChinesePublished", "Published");
    }
}
