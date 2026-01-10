# Snout Authenticator

Snout is a secure TOTP (Time-based One-Time Password) authenticator application designed to protect your accounts against common attack scenarios while prioritizing user security.

---

## What is Snout?

Snout is an Android-based authenticator app that generates time-based one-time passwords (TOTP) for two-factor authentication (2FA). It focuses on strong security practices, especially around backup and seed handling.

---

## What attack scenarios does it protect against?

Snout helps protect against:
- Account takeover through stolen passwords
- Phishing attacks that rely on password-only authentication
- Unauthorized access when a second factor is required

It does **not** protect against:
- Compromised devices
- Malware with system-level access
- Users manually exposing their seed or backup data

---

## Backup tradeoffs and security warning

Snout allows backups for recovery purposes, but **you should NEVER store your seed phrase on the same device**.

Storing both the authenticator and its backup on one device defeats the purpose of two-factor authentication and significantly reduces security.

---

## Why use Snout over alternatives?

You might ask:
- “My password manager can do TOTP”
- “Google Authenticator supports TOTP”
- “Aegis also supports TOTP”

Snout is especially suited for users who want stronger security guarantees and are willing to trade some convenience for safer backup practices.


Snout differentiates itself by:
- Strong emphasis on secure backup handling
- Minimal and focused feature set
- Prioritizing security decisions over convenience

---

## Project status

This project is under active development. Contributions, feedback, and documentation improvements are welcome.
