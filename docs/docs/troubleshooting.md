---
title: Troubleshooting
sidebar_position: 80
---
### Persistent Tool Hang-Ups: Potential Solutions

Experiencing endless hang-ups after server creation, accompanied by timeouts? This could stem from SSH key issues. Common culprits include passphrase-protected keys or outdated keys (especially given recent crypto deprecations in newer operating systems). A potential fix is setting `use_ssh_agent` to `true` enabling SSH agent usage.

Unfamiliar with SSH agents? Gain a clearer understanding by exploring [this comprehensive guide](https://smallstep.com/blog/ssh-agent-explained/) on SSH agent basics.