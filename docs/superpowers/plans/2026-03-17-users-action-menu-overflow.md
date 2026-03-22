# Users Action Menu Overflow Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Users page action menu so pagination to the last rows does not clip menu items and the menu automatically opens upward when there is not enough space below the trigger.

**Architecture:** Keep the existing inline action menu in `Users.jsx`, but track per-row menu direction and compute whether the dropdown should open upward based on viewport space. Remove the parent card clipping that currently hides lower menu items and verify the regression with a dedicated page test.

**Tech Stack:** React 19, Vite, Vitest, Testing Library

---

## Chunk 1: Regression Test

### Task 1: Reproduce the clipped/floating menu behavior in a test

**Files:**
- Create: `frontend/src/test/Users.test.jsx`
- Modify: `frontend/src/pages/Users.jsx`
- Test: `frontend/src/test/Users.test.jsx`

- [ ] **Step 1: Write the failing test**
  Render `Users`, mock two pages of user data, navigate to page 2, open the action menu on the only visible row, and assert the menu exposes all actions including the last destructive entry.

- [ ] **Step 2: Run test to verify it fails**
  Run: `npm test -- Users.test.jsx`
  Expected: FAIL because the current component does not expose a reliable upward-opening state and the regression conditions are not handled.

## Chunk 2: Minimal Fix

### Task 2: Make the menu escape clipping and auto-flip upward near the bottom

**Files:**
- Modify: `frontend/src/pages/Users.jsx`
- Test: `frontend/src/test/Users.test.jsx`

- [ ] **Step 1: Add menu direction state and measurement logic**
  Track the open menu id together with its direction. On trigger click, measure available space below the clicked trigger and open upward if that space is smaller than the menu height threshold.

- [ ] **Step 2: Remove the clipping from the users table card**
  Allow the action dropdown to render outside the card body so lower actions are not visually cut off.

- [ ] **Step 3: Keep behavior localized**
  Do not refactor shared components or other pages. Preserve existing menu items and close-on-outside-click behavior.

- [ ] **Step 4: Run the targeted test to verify it passes**
  Run: `npm test -- Users.test.jsx`
  Expected: PASS

## Chunk 3: Verification

### Task 3: Verify the page-level regression and general frontend checks

**Files:**
- Modify: `frontend/src/pages/Users.jsx`
- Create: `frontend/src/test/Users.test.jsx`

- [ ] **Step 1: Run the targeted regression test**
  Run: `npm test -- Users.test.jsx`
  Expected: PASS

- [ ] **Step 2: Run the broader frontend test suite**
  Run: `npm test`
  Expected: Existing tests and the new regression test pass.
