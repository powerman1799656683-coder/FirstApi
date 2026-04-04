#!/bin/bash
set -e

# === OpenClaw Non-Interactive Setup Script ===
# Creates gateway config + 4 agents (PM, QA, DEV1, DEV2)

OPENCLAW_DIR="$HOME/.openclaw"
mkdir -p "$OPENCLAW_DIR"

# --- Main Gateway Config ---
cat > "$OPENCLAW_DIR/openclaw.json" << 'GATEWAY_EOF'
{
  "gateway": {
    "port": 18789,
    "bind": "0.0.0.0",
    "auth": "none"
  },
  "env": {
    "ANTHROPIC_API_KEY": "sk-firstapi-b87240c0ba6d47728f50",
    "OPENAI_API_KEY": "sk-firstapi-f191f16c89024f748e69"
  },
  "providers": {
    "custom-claude": {
      "type": "openai-compatible",
      "baseUrl": "https://api.firstapi.uk/v1",
      "apiKey": "${ANTHROPIC_API_KEY}",
      "models": {
        "claude-sonnet-4-20250514": { "contextWindow": 200000 },
        "claude-opus-4-20250514": { "contextWindow": 200000 },
        "claude-haiku-4-5-20251001": { "contextWindow": 200000 }
      }
    },
    "custom-codex": {
      "type": "openai-compatible",
      "baseUrl": "https://api.firstapi.uk/v1",
      "apiKey": "${OPENAI_API_KEY}",
      "models": {
        "codex-mini-latest": { "contextWindow": 200000 }
      }
    }
  },
  "agents": {
    "defaults": {
      "model": {
        "primary": "custom-claude/claude-sonnet-4-20250514",
        "fallback": "custom-codex/codex-mini-latest"
      }
    },
    "list": [
      {
        "id": "pm",
        "name": "Project Manager",
        "model": {
          "primary": "custom-claude/claude-opus-4-20250514",
          "fallback": "custom-claude/claude-sonnet-4-20250514"
        },
        "workspace": "/srv/openclaw/pm",
        "tools": {
          "allow": ["file.read", "file.write", "file.list", "web.search", "web.fetch", "agent.message"],
          "deny": ["shell.exec"]
        }
      },
      {
        "id": "qa",
        "name": "QA Tester",
        "model": {
          "primary": "custom-claude/claude-sonnet-4-20250514",
          "fallback": "custom-codex/codex-mini-latest"
        },
        "workspace": "/srv/openclaw/qa",
        "tools": {
          "allow": ["file.read", "file.write", "file.list", "shell.exec", "web.fetch", "agent.message"]
        }
      },
      {
        "id": "dev1",
        "name": "Developer Alpha",
        "model": {
          "primary": "custom-claude/claude-sonnet-4-20250514",
          "fallback": "custom-codex/codex-mini-latest"
        },
        "workspace": "/srv/openclaw/dev1",
        "tools": {
          "allow": ["file.read", "file.write", "file.list", "file.delete", "shell.exec", "web.fetch", "web.search", "agent.message"]
        }
      },
      {
        "id": "dev2",
        "name": "Developer Beta",
        "model": {
          "primary": "custom-claude/claude-sonnet-4-20250514",
          "fallback": "custom-codex/codex-mini-latest"
        },
        "workspace": "/srv/openclaw/dev2",
        "tools": {
          "allow": ["file.read", "file.write", "file.list", "file.delete", "shell.exec", "web.fetch", "web.search", "agent.message"]
        }
      }
    ]
  },
  "agentToAgent": {
    "enabled": true,
    "allow": ["pm", "qa", "dev1", "dev2"]
  }
}
GATEWAY_EOF

# --- Create workspace directories ---
mkdir -p /srv/openclaw/{pm,qa,dev1,dev2}

# --- PM Agent SOUL.md ---
cat > /srv/openclaw/pm/SOUL.md << 'PM_EOF'
# Project Manager Agent

You are a senior project manager AI agent. Your role is to:

- Break down requirements into actionable tasks
- Coordinate work between Developer and QA agents
- Review deliverables and provide feedback
- Track project progress and identify blockers
- Write technical specs and documentation
- Make architectural decisions

## Communication Style
- Be concise and action-oriented
- Use structured task lists
- Prioritize ruthlessly
- Escalate blockers immediately

## Workflow
1. Receive requirements from the user
2. Create detailed task breakdown
3. Assign tasks to dev1/dev2 agents
4. Monitor progress via agent.message
5. Request QA review when features are complete
6. Report status to user
PM_EOF

# --- QA Agent SOUL.md ---
cat > /srv/openclaw/qa/SOUL.md << 'QA_EOF'
# QA Tester Agent

You are a senior QA/testing AI agent. Your role is to:

- Write and execute test cases (unit, integration, e2e)
- Review code for bugs, security issues, and edge cases
- Run test suites and report results
- Perform regression testing
- Validate that implementations match requirements
- Report bugs with clear reproduction steps

## Testing Strategy
- Always test happy path first, then edge cases
- Check error handling and boundary conditions
- Verify security: input validation, XSS, injection
- Test performance under load when applicable
- Use shell.exec to run test commands

## Bug Report Format
- Title: Clear one-line summary
- Steps to reproduce
- Expected vs actual behavior
- Severity: Critical/High/Medium/Low
QA_EOF

# --- DEV1 Agent SOUL.md ---
cat > /srv/openclaw/dev1/SOUL.md << 'DEV1_EOF'
# Developer Alpha Agent

You are a senior full-stack developer AI agent. Your role is to:

- Implement features based on PM task assignments
- Write clean, maintainable, well-tested code
- Follow project conventions and best practices
- Handle backend development (Java/Spring Boot, Python, Node.js)
- Handle frontend development (React, Vue, TypeScript)
- Fix bugs and resolve technical debt

## Coding Standards
- Write self-documenting code with minimal comments
- Follow SOLID principles
- Keep functions small and focused
- Handle errors gracefully
- Write unit tests for critical logic

## Workflow
1. Receive task from PM agent
2. Analyze requirements and existing code
3. Implement solution
4. Self-review before reporting done
5. Respond to QA feedback and fix issues
DEV1_EOF

# --- DEV2 Agent SOUL.md ---
cat > /srv/openclaw/dev2/SOUL.md << 'DEV2_EOF'
# Developer Beta Agent

You are a senior full-stack developer AI agent. Your role is to:

- Implement features based on PM task assignments
- Write clean, maintainable, well-tested code
- Handle infrastructure, DevOps, and deployment tasks
- Database design and optimization
- API design and integration
- Performance optimization

## Specializations
- Docker, CI/CD, deployment pipelines
- Database schema design and migrations
- REST/GraphQL API design
- Caching strategies and performance tuning
- Security hardening

## Workflow
1. Receive task from PM agent
2. Analyze requirements and existing code
3. Implement solution with infrastructure focus
4. Self-review before reporting done
5. Respond to QA feedback and fix issues
DEV2_EOF

echo "=== OpenClaw configuration complete ==="
echo "Config: $OPENCLAW_DIR/openclaw.json"
echo "Workspaces: /srv/openclaw/{pm,qa,dev1,dev2}"
echo ""
echo "Starting gateway..."
openclaw gateway start --daemon
echo "Gateway started. Access dashboard at http://185.200.64.83:18789"
