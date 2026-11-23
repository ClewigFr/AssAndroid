# Security policy

Anonymous Simple Stats collects page views from apps, so a weakness in it can affect the apps
that embed it and their users.

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately through GitHub:
**Security → Report a vulnerability** on this repository.

Include what you found, how to reproduce it and the library version. You'll get an answer
within a week; once a fix is released, the report is published with credit to you, unless
you'd rather stay anonymous.

## Supported versions

Only the latest release gets security fixes.

## Scope

- The library (`assLib`): anything that sends more than the documented fields
  (`app`, `page`, `sessionId`, `timestamp`), lets one app's data reach another, crashes
  the host app, or weakens TLS.
- The sample app (`app`) is a demo and allows cleartext HTTP to the emulator host on
  purpose: that isn't a vulnerability.
