# Snout
Snout is a security-focused passkey and TOTP authenticator.

*Snout is currently pre-release — expect bugs and breaking changes!*

### Certificate fingerprint
This is the SHA256 hash of the Snout signing certificate. Use it to verify the app's authenticity.

```
b7d455b87a1224e78af319e4af5e15a6af670fbb6b6906a0f3f527c0f36f1d83
```

Note that the fingerprint *will* change when Snout leaves pre-release status.


## Features
* **Passkeys:** get rid of usernames and passwords entirely, or use a stronger second factor.
* **One-time codes:** for the services that don't support passkeys, Snout acts as an OTP authenticator.
* **Hardware-backed security:** passkeys and OTP secrets are stored in secure hardware, and are
    literally impossible to steal from your device.
* **Strong encrypted backups:** backups — if enabled — are protected by a random seed, stored
    offline as a QR code or a BIP-39 seed phrase.
* **Degoogled:** Snout does not depend on Google Play Services, and is developed and tested
    on [GrapheneOS](https://grapheneos.org).
* **Offline:** Snout does not collect any metrics or user data, or depend on proprietary servers —
    it doesn't even require a network connection. *You* are in control of *your* data.


## Why (or why not) Snout?
Snout aims to be the most secure authenticator. To that end, it sacrifices some conveniences
provided by many other authenticators.

### Compared to your password manager
Second factor credentials, such as OTP secrets or passkeys used as a second factor, do not belong
in your password manager. By storing them side by side with the passwords they are intended to act
as a second factor for, you are effectively *removing* the second factor.

Furthermore, most password managers are designed to store things for later retrieval.
This is a natural consequence of being a *password* manager: if you can't read or copy your password,
you can't use it.

This is not how passkeys or OTP secrets work.
When using either to authenticate, you provide a *proof* that you are in possession of the credential,
without ever revealing the credential itself.
Allowing such credentials to be retrieved at all is a security problem, not a feature!

### Compared to Aegis, Google Authenticator, etc.
Most other authenticators put a higher premium on convenience than Snout does, allowing credentials
to be read and transferred to other devices at will, store your credentials with cloud providers,
etc.

Snout instead stores your credentials in the secure element of your device, from which there is no
escape — ever. Even with backups enabled — which is optional — recovery is only possible using
your offline backup seed to ensure that even the device that creates your backups can't access them. 


## Security design
This section briefly describes the steps taken by Snout to keep your secrets safe.

### Credential storage
Snout keeps its account database in an SQLite database encrypted with a 256 bit random
data encryption key (DEK) using SQLCipher.
The DEK is encrypted with a 256 bit AES key encryption key (KEK) stored in the device's
secure element (SE) or TEE through Android KeyStore, depending on available hardware.
By default, user authentication is required to access the KEK for unlocking the database.

The DEK is only kept in memory while the database is unlocked.
The database can be locked for the following reasons:

* The user explicitly locks it.
* The screen lock was engaged.
* Snout has been in the background for too long (30 seconds by default).

When either of these conditions are met, the database is closed and the DEK is wiped from memory.

The account database only contains account metadata. It does *not* contain any credentials (unless
backups are enabled — see [Backups](#Backups) for more information).
Credentials are instead stored in SE/TEE, and always require user authentication to access.

### Backups
If backups are enabled, Snout stores a copy of each credential in the account database.
This copy is encrypted using a 256 bit AES key derived from a 256 bit backup seed.
The key is stored in SE/TEE, and only approved for encryption use — crucially *not* for decryption.

The backup seed is randomly generated on first start, and displayed to the user in the form of
either a BIP-39 seed phrase or a QR code for printing. The user is strongly encouraged to
store their seed phrase offline, and advised that storing it on the same device where Snout is
installed is a terrible idea.

As the backup secrets are protected by both the backup key and the database encryption keys,
the user's credentials are at least as well protected as with any other authenticator even if the user
were to store their backup seed in plain text on the same device.

To keep this level of protection intact, backups are never created automatically but only by
explicit user request.

### Authentication
Snout requires user authentication using either class 3 biometrics (e.g. fingerprint) or device
credential (e.g. PIN/password) to use any credential and to unlock the account database.

For symmetric credentials (i.e. OTP secrets and the database KEK), the key remains unlocked
for five seconds following authentication, whereas asymmetric credentials (i.e. passkeys)
only can be used for a single operation without having to authenticate the user again.

### Credential creation
Credentials are generated in device memory, then immediately stored in SE/TEE hardware and (if enabled)
encrypted for backup, and finally wiped from memory.
This means that there is a small risk of the credential persisting in device memory for some time
after creation.

This small time window could theoretically be exploited either by a compromised operating system
or through a cold boot attack.
To mitigate this risk, it is strongly recommended to run Snout on a device without root permissions
and with a locked bootloader.

### Snooping mitigations
To prevent shoulder surfers, malware or "well-meaning" but integrity-challenged AI scrapers from
reading one-time codes, Snout takes the following precautions:

* One-time codes are not displayed or generated until the user selects a specific account and
  authenticates.
* Screenshots and screen recording are disabled for Snout.
* Secrets can be configured to be hidden from screen readers, but default to visible to avoid
  excluding visually impaired users.


## Contributing
Unfortunately, we are unable to accept external contributions at this point.