# Clash Verge Rev IPRoyal Split Design

## Summary

Configure the local Windows machine to route traffic through Clash Verge Rev using `TUN + Rule` mode, with `IPRoyal` as the outbound HTTP proxy for non-China traffic.

## Goals

- Keep local and LAN traffic direct
- Keep China traffic direct
- Route other traffic through IPRoyal
- Preserve the current remote subscription instead of replacing it
- Make the change survive subscription refreshes

## Non-Goals

- No true multi-hop proxy chain
- No changes to the application code in this repository
- No replacement of the user's existing remote profile

## Design

### Runtime Mode

- Set Clash core mode to `rule`
- Keep `TUN` enabled
- Enable `strict-route`
- Expand `dns-hijack` to cover both UDP and TCP port 53

### Profile Integration

The active profile is a remote subscription with enhancement fragments for:

- proxies
- groups
- rules

The IPRoyal node will be added through the active profile's enhancement files instead of editing the remote subscription body directly.

### Routing Strategy

Add a custom proxy group:

- `IPRoyal-Select`

Add high-priority rules:

- localhost and private/LAN networks direct
- `GEOSITE,CN` direct
- `GEOIP,CN` direct
- final `MATCH` to `IPRoyal-Select`

This intentionally overrides the subscription's later catch-all routing so the machine behaves like a stable split-tunnel setup.

### Verification

- Back up existing local Clash Verge Rev files
- Confirm the patched files contain the expected settings
- Confirm the generated merged config contains the new proxy, group, and rules
- Note proxy connectivity results separately because the upstream provider can reject connections for reasons outside the local config
