package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelayChatCompletionRequest {
    private String model;
    private List<Message> messages = new ArrayList<Message>();
    private Boolean stream;
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    private Object stop;
    private String user;
    private List<Object> tools;
    @JsonProperty("tool_choice")
    private Object toolChoice;
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;
    @JsonIgnore
    private final Map<String, Object> unsupportedFields = new LinkedHashMap<String, Object>();

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Object getStop() {
        return stop;
    }

    public void setStop(Object stop) {
        this.stop = stop;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<Object> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    @JsonIgnore
    public Map<String, Object> getUnsupportedFields() {
        return unsupportedFields;
    }

    @JsonIgnore
    public boolean hasUnsupportedFields() {
        return !unsupportedFields.isEmpty();
    }

    @JsonIgnore
    public boolean hasOpenAiToolCallingFields() {
        if ((tools != null && !tools.isEmpty()) || toolChoice != null || parallelToolCalls != null) {
            return true;
        }
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (Message message : messages) {
            if (message != null && message.hasToolCallingFields()) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public int resolveMaxTokens() {
        if (maxTokens != null && maxTokens.intValue() > 0) {
            return maxTokens.intValue();
        }
        if (maxCompletionTokens != null && maxCompletionTokens.intValue() > 0) {
            return maxCompletionTokens.intValue();
        }
        return 1024;
    }

    @JsonAnySetter
    public void addUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    public static class Message {
        private String role;
        private Object content;
        @JsonProperty("tool_calls")
        private List<Object> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;
        @JsonIgnore
        private final Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

        public Message() {
        }

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Object getRawContent() {
            return content;
        }

        @SuppressWarnings("unchecked")
        public String getContent() {
            if (content == null) {
                return null;
            }
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object part : (List<Object>) content) {
                    if (part instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) part;
                        Object text = map.get("text");
                        if (text != null) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(text.toString());
                        }
                    } else if (part instanceof String) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(part);
                    }
                }
                return sb.toString();
            }
            return content.toString();
        }

        public void setContent(Object content) {
            this.content = content;
        }

        public List<Object> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<Object> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public void setToolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }

        @JsonAnySetter
        public void addExtraField(String name, Object value) {
            extraFields.put(name, value);
        }

        @JsonIgnore
        public boolean hasToolCallingFields() {
            return (toolCalls != null && !toolCalls.isEmpty())
                    || (toolCallId != null && !toolCallId.trim().isEmpty());
        }
    }

    public static class Builder {
        private final RelayChatCompletionRequest request = new RelayChatCompletionRequest();

        public Builder model(String model) {
            request.setModel(model);
            return this;
        }

        public Builder stream(Boolean stream) {
            request.setStream(stream);
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            request.setMaxTokens(maxTokens);
            return this;
        }

        public Builder addMessage(String role, String content) {
            request.getMessages().add(new Message(role, content));
            return this;
        }

        public RelayChatCompletionRequest build() {
            return request;
        }
    }
}
