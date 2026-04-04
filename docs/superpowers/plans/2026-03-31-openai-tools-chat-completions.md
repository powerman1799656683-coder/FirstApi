# OpenAI Tools Chat Completions Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing OpenAI chat-completions relay accept and forward tool-calling requests in both non-streaming and streaming modes without changing Claude tool behavior.

**Architecture:** Extend the request model so OpenAI tool fields are no longer rejected or dropped, then add provider-aware validation so Claude-routed chat-completions requests still fail clearly when those fields are present. Keep the OpenAI upstream response path as a pass-through.

**Tech Stack:** Spring Boot, Jackson, JUnit 5, Mockito, MockMvc, MockWebServer

---

### Task 1: Lock Failing Relay Tests

**Files:**
- Modify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Write the failing non-streaming OpenAI tools relay test**
- [ ] **Step 2: Run the targeted backend tests and confirm the new test fails for the expected reason**
- [ ] **Step 3: Write the failing streaming OpenAI tools relay test**
- [ ] **Step 4: Run the targeted backend tests and confirm the streaming case fails for the expected reason**
- [ ] **Step 5: Write the failing Claude rejection test for OpenAI tool fields**
- [ ] **Step 6: Run the targeted backend tests and confirm the Claude rejection case fails or exposes the current silent-drop behavior**

### Task 2: Preserve OpenAI Tool Fields

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/model/RelayChatCompletionRequest.java`

- [ ] **Step 1: Add top-level OpenAI tool fields to the request model**
- [ ] **Step 2: Add message-level passthrough support for tool-related fields**
- [ ] **Step 3: Run the targeted backend tests and verify request parsing/serialization failures are resolved**

### Task 3: Add Provider-Aware Validation

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`

- [ ] **Step 1: Add provider-aware validation for OpenAI tool payloads**
- [ ] **Step 2: Ensure OpenAI-routed requests pass and Claude-routed requests fail clearly**
- [ ] **Step 3: Run the targeted relay tests and verify they pass**

### Task 4: Verify End-To-End Relay Behavior

**Files:**
- Verify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`
- Verify: `backend/src/test/java/com/firstapi/backend/service/RelayServiceTest.java`

- [ ] **Step 1: Run the focused relay test suite**
- [ ] **Step 2: Run the broader backend test suite if the focused suite is green**
- [ ] **Step 3: Summarize supported OpenAI tool-calling behavior and remaining non-goals**
