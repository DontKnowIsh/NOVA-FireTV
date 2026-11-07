#!/usr/bin/env python3
"""Route every first-run sign-in directly to username and password."""

from pathlib import Path


root = Path("jellyfin-src/app/src/main")
# Quick Connect asks for an app/browser session that NOVA does not provide.
# All entry points, including the password tile on the server screen and the
# Add account action, should open the Jellyfin username/password form.
login = root / "java/org/jellyfin/androidtv/ui/startup/fragment/UserLoginFragment.kt"
content = login.read_text(encoding="utf-8")
old = """\t\t// Open initial fragment
\t\tif (skipQuickConnect) setLoginMethod<UserLoginCredentialsFragment>()
\t\telse setLoginMethod<UserLoginQuickConnectFragment>()"""
new = """\t\t// NOVA signs in with the server username and password.
\t\tsetLoginMethod<UserLoginCredentialsFragment>()"""
if content.count(old) != 1:
    raise RuntimeError("Expected NOVA sign-in routing was not found")
login.write_text(content.replace(old, new, 1), encoding="utf-8")
