package com.firstapi.backend.controller;

import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.service.RelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RelayControllerStandaloneTest {

    @Mock
    private RelayService relayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RelayController(relayService))
                .setControllerAdvice(new RelayExceptionHandler(), new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsPostForModelsEndpointToSupportGatewayChecks() throws Exception {
        mockMvc.perform(post("/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o"));
    }

    @Test
    void delegatesAnthropicCompatibleMessagesEndpoint() throws Exception {
        RelayResult result = new RelayResult();
        result.setStatusCode(200);
        result.setContentType(MediaType.APPLICATION_JSON_VALUE);
        result.setBody("{\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":\"hello from claude\"}]}");
        when(relayService.relayClaudeMessages(eq("sk-firstapi"), isNull(), eq("2023-06-01"), any()))
                .thenReturn(result);

        mockMvc.perform(post("/v1/messages")
                        .header("x-api-key", "sk-firstapi")
                        .header("anthropic-version", "2023-06-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"max_tokens\":16,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("message"))
                .andExpect(jsonPath("$.content[0].text").value("hello from claude"));

        verify(relayService).relayClaudeMessages(eq("sk-firstapi"), isNull(), eq("2023-06-01"), any());
    }

    @Test
    void delegatesOpenAiToolCallingPayloadsToRelayService() throws Exception {
        RelayResult result = new RelayResult();
        result.setStatusCode(200);
        result.setContentType(MediaType.APPLICATION_JSON_VALUE);
        result.setBody("""
                {"id":"chatcmpl-tool","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_read_file","type":"function","function":{"name":"read_file","arguments":"{\\"path\\":\\"README.md\\"}"}}]},"finish_reason":"tool_calls"}]}
                """);
        when(relayService.relayChatCompletion(eq("Bearer sk-firstapi"), any()))
                .thenReturn(result);

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o-mini",
                                  "tool_choice": "auto",
                                  "parallel_tool_calls": true,
                                  "tools": [
                                    {
                                      "type": "function",
                                      "function": {
                                        "name": "read_file",
                                        "parameters": {
                                          "type": "object",
                                          "properties": {
                                            "path": {
                                              "type": "string"
                                            }
                                          }
                                        }
                                      }
                                    }
                                  ],
                                  "messages": [
                                    {
                                      "role": "user",
                                      "content": "Read the README"
                                    },
                                    {
                                      "role": "assistant",
                                      "content": null,
                                      "tool_calls": [
                                        {
                                          "id": "call_list_files",
                                          "type": "function",
                                          "function": {
                                            "name": "list_files",
                                            "arguments": "{}"
                                          }
                                        }
                                      ]
                                    },
                                    {
                                      "role": "tool",
                                      "tool_call_id": "call_list_files",
                                      "content": "[\\"README.md\\"]"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.choices[0].message.tool_calls[0].function.name").value("read_file"));

        ArgumentCaptor<RelayChatCompletionRequest> requestCaptor =
                ArgumentCaptor.forClass(RelayChatCompletionRequest.class);
        verify(relayService).relayChatCompletion(eq("Bearer sk-firstapi"), requestCaptor.capture());

        RelayChatCompletionRequest request = requestCaptor.getValue();
        assertThat(request.getToolChoice()).isEqualTo("auto");
        assertThat(request.getParallelToolCalls()).isTrue();
        assertThat(request.getTools()).hasSize(1);
        assertThat(request.getMessages()).hasSize(3);
        assertThat(request.getMessages().get(1).getToolCalls()).hasSize(1);
        assertThat(request.getMessages().get(2).getToolCallId()).isEqualTo("call_list_files");
    }
}
