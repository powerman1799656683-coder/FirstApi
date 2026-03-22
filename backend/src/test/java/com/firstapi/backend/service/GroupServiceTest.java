package com.firstapi.backend.service;

import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private AccountRepository accountRepository;

    private GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, accountRepository);
    }

    @Test
    void createRejectsBlankAccountType() {
        GroupItem.Request req = new GroupItem.Request();
        req.setName("openai-plus");
        req.setPlatform("OpenAI");
        req.setAccountType(" ");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .matches(ex -> ((ResponseStatusException) ex).getStatusCode().equals(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createRejectsAccountTypeOutsidePlatformWhitelist() {
        GroupItem.Request req = new GroupItem.Request();
        req.setName("openai-invalid");
        req.setPlatform("OpenAI");
        req.setAccountType("Claude Code");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("accountType");
    }

    @Test
    void createPersistsValidPlatformAccountType() {
        GroupItem.Request req = new GroupItem.Request();
        req.setName("openai-plus");
        req.setPlatform("OpenAI");
        req.setAccountType("ChatGPT Plus");

        when(groupRepository.save(org.mockito.ArgumentMatchers.any(GroupItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());

        GroupItem created = service.create(req);

        assertThat(created.getAccountType()).isEqualTo("ChatGPT Plus");
    }

    @Test
    void createEnrichesAccountCountByPlatformAndAccountType() {
        GroupItem.Request req = new GroupItem.Request();
        req.setName("claude-code");
        req.setPlatform("Anthropic");
        req.setAccountType("Claude Code");

        when(groupRepository.save(org.mockito.ArgumentMatchers.any(GroupItem.class)))
                .thenAnswer(invocation -> {
                    GroupItem item = invocation.getArgument(0);
                    item.setId(22L);
                    return item;
                });
        when(groupRepository.findAll()).thenReturn(List.of(group(22L, "Anthropic", "Claude Code")));
        when(accountRepository.findAll()).thenReturn(List.of(
                account(1L, "Anthropic", "Claude Code"),
                account(2L, "Anthropic", "Claude Max"),
                account(3L, "Anthropic", "Claude Code")
        ));

        GroupItem created = service.create(req);

        assertThat(created.getAccountTotal()).isEqualTo(2);
        assertThat(created.getAccountCount()).contains("2");
    }

    @Test
    void listCountsAccountsByPlatformAndAccountType() {
        GroupItem group = new GroupItem();
        group.setId(11L);
        group.setName("openai-plus");
        group.setPlatform("OpenAI");
        group.setAccountType("ChatGPT Plus");
        group.setStatus("正常");

        when(groupRepository.findAll()).thenReturn(List.of(group));
        when(accountRepository.findAll()).thenReturn(List.of(
                account(1L, "OpenAI", "ChatGPT Plus"),
                account(2L, "OpenAI", "ChatGPT Plus"),
                account(3L, "OpenAI", "ChatGPT Pro"),
                account(4L, "Anthropic", "Claude Code")
        ));

        GroupItem item = service.list(null, null, null, null).getItems().get(0);

        assertThat(item.getAccountTotal()).isEqualTo(2);
        assertThat(item.getAccountCount()).contains("2");
    }

    private AccountItem account(Long id, String platform, String accountType) {
        AccountItem item = new AccountItem();
        item.setId(id);
        item.setPlatform(platform);
        item.setAccountType(accountType);
        return item;
    }

    private GroupItem group(Long id, String platform, String accountType) {
        GroupItem item = new GroupItem();
        item.setId(id);
        item.setName("g-" + id);
        item.setPlatform(platform);
        item.setAccountType(accountType);
        return item;
    }
}
