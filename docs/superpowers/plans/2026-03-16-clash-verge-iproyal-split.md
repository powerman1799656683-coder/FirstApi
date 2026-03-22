# Clash Verge Rev IPRoyal Split Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the local Clash Verge Rev installation to run Windows in `TUN + Rule` mode and send non-China traffic through an IPRoyal HTTP proxy.

**Architecture:** Preserve the existing remote subscription and inject the IPRoyal node, a dedicated selector group, and high-priority split rules through Clash Verge Rev enhancement files. Update the generated local runtime config so the Clash core actually runs in `rule` mode with strict routing and DNS hijacking enabled.

**Tech Stack:** Clash Verge Rev, Mihomo YAML config, PowerShell

---

## Chunk 1: Document And Protect Current State

### Task 1: Record the intended configuration

**Files:**
- Create: `docs/superpowers/specs/2026-03-16-clash-verge-iproyal-split-design.md`
- Create: `docs/superpowers/plans/2026-03-16-clash-verge-iproyal-split.md`

- [ ] **Step 1: Save the design and plan files**
- [ ] **Step 2: Re-read them for consistency**

### Task 2: Back up active Clash Verge Rev files

**Files:**
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\config.yaml`
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\pidYrVhCG1I1.yaml`
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\gBHeiwFnYyR5.yaml`
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\rC8hk7yOlYOt.yaml`

- [ ] **Step 1: Copy the active files to timestamped backups**
- [ ] **Step 2: Confirm the backup files exist**

## Chunk 2: Apply Split Routing

### Task 3: Patch runtime config

**Files:**
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\config.yaml`

- [ ] **Step 1: Change `mode` to `rule`**
- [ ] **Step 2: Set `tun.enable` to `true`**
- [ ] **Step 3: Set `tun.strict-route` to `true`**
- [ ] **Step 4: Add `tcp://any:53` to `tun.dns-hijack`**

### Task 4: Patch active enhancement fragments

**Files:**
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\pidYrVhCG1I1.yaml`
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\gBHeiwFnYyR5.yaml`
- Modify: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\rC8hk7yOlYOt.yaml`

- [ ] **Step 1: Add the IPRoyal HTTP proxy**
- [ ] **Step 2: Add the `IPRoyal-Select` proxy group**
- [ ] **Step 3: Prepend split rules for LAN/local/CN direct and final `MATCH` to `IPRoyal-Select`**

## Chunk 3: Validate Effective Output

### Task 5: Inspect effective files

**Files:**
- Read: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\config.yaml`
- Read: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\pidYrVhCG1I1.yaml`
- Read: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\gBHeiwFnYyR5.yaml`
- Read: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\profiles\rC8hk7yOlYOt.yaml`
- Read: `C:\Users\power\AppData\Roaming\io.github.clash-verge-rev.clash-verge-rev\clash-verge.yaml`

- [ ] **Step 1: Confirm the local files contain the expected changes**
- [ ] **Step 2: Confirm the merged config exposes the new proxy, group, and rules after reload or regeneration**
- [ ] **Step 3: Report any upstream connectivity failure separately from config correctness**
