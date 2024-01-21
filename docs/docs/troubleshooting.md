---
title: Troubleshooting
sidebar_position: 80
---
### Persistent Tool Hang-Ups: Potential Solutions

Experiencing endless hang-ups after server creation, accompanied by timeouts? This could stem from SSH key issues. Common culprits include passphrase-protected keys or outdated keys (especially given recent crypto deprecations in newer operating systems). A potential fix is setting `use_ssh_agent` to `true` enabling SSH agent usage.

Unfamiliar with SSH agents? Gain a clearer understanding by exploring [this comprehensive guide](https://smallstep.com/blog/ssh-agent-explained/) on SSH agent basics.

### If you get this error while trying to SSH into server

It just means that previously also you had connected to same ip and then possibly deleted the server. 
Hence there is another fingerprint while trying to connect and its asking you to reverify. 

```bash
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
IT IS POSSIBLE THAT SOMEONE IS DOING SOMETHING NASTY!
Someone could be eavesdropping on you right now (man-in-the-middle attack)!
It is also possible that a host key has just been changed.
The fingerprint for the ED25519 key sent by the remote host is
```

To fix it just delete the ip corresponding to your remote server in the `~/.ssh/known_hosts` file. 
Or else just delete the whole file itself and try to reconnect. `rm ~/.ssh/known_hosts`