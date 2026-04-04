# Responses Relay And OpenClaw Debug Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the local `/v1/responses` non-stream relay contract and capture enough evidence to distinguish OpenClaw runtime retries from project relay bugs.

**Architecture:** Keep the current controller split between streaming and non-streaming calls, but make the non-stream path normalize any SSE-style upstream body into a single JSON responses payload before returning it. In parallel, preserve the OpenClaw investigation as evidence-only work: inspect local session/config/runtime artifacts without restarting services or changing unrelated runtime behavior.

**Tech Stack:** Spring Boot, MockMvc, MockWebServer, JUnit 5, local OpenClaw runtime/session logs

---

## Chunk 1: Relay Contract Fix

### Task 1: Add failing `/v1/responses` regression tests

**Files:**
- Modify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`
- Test: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Add a failing non-stream responses test**
- [ ] **Step 2: Add a failing standalone controller delegation test if needed**
- [ ] **Step 3: Run the focused controller tests and verify the new case fails for the expected reason**

### Task 2: Implement minimal non-stream normalization

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java`
- Modify: `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`

- [ ] **Step 1: Keep streaming `/v1/responses` path unchanged**
- [ ] **Step 2: Normalize non-stream SSE-style responses into JSON before returning**
- [ ] **Step 3: Preserve usage extraction and current relay metadata**

### Task 3: Verify relay behavior

**Files:**
- Test: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Run focused relay controller tests**
- [ ] **Step 2: Run any adjacent relay tests affected by the change**
- [ ] **Step 3: Record actual pass/fail evidence before claiming completion**

## Chunk 2: OpenClaw Runtime Root Cause

### Task 4: Preserve local runtime evidence

**Files:**
- Read only: `%USERPROFILE%/.openclaw/openclaw.json`
- Read only: `%USERPROFILE%/.openclaw/agents/main/sessions/*.jsonl`
- Read only: `%USERPROFILE%/.openclaw/agents/main/sessions/sessions.json`

- [ ] **Step 1: Confirm Telegram routing is bound only to `main`**
- [ ] **Step 2: Confirm repeated replies align with `errorMessage=\"terminated\"` chains**
- [ ] **Step 3: Compare against direct upstream `/v1/responses` behavior**

### Task 5: Produce a concrete diagnosis

**Files:**
- No file edits required unless adding internal notes later

- [ ] **Step 1: Separate project relay bug from OpenClaw runtime retry behavior**
- [ ] **Step 2: State what is fixed in-project and what remains local-runtime behavior**
- [ ] **Step 3: Avoid any restart/config mutation unless explicitly requested**
