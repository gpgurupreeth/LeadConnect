#!/usr/bin/env python3
"""
Administrative utility to generate signed, short-lived JWT tokens
for Android handsets connecting to the WhatsApp backend proxy.

Usage:
    python generate_token.py [--device-id <ID>] [--expiry-minutes <MIN>] [--secret <SECRET>]
"""

import argparse
import os
import sys
import uuid
from pathlib import Path
from dotenv import load_dotenv

# Ensure local backend-proxy package can be imported
sys.path.insert(0, str(Path(__file__).parent))

from auth import create_device_jwt
from config import INSECURE_SECRET_PATTERNS


def generate_token_cli():
    # Load .env if present
    env_path = Path(__file__).parent / ".env"
    if env_path.exists():
        load_dotenv(dotenv_path=env_path)

    parser = argparse.ArgumentParser(
        description="Generate signed device JWT for Android Sales Caller Settings."
    )
    parser.add_argument(
        "--device-id",
        type=str,
        default=None,
        help="Device identifier (defaults to a newly generated UUID)",
    )
    parser.add_argument(
        "--expiry-minutes",
        type=int,
        default=int(os.getenv("JWT_EXPIRATION_MINUTES", "5")),
        help="Token expiration lifetime in minutes (default: 5)",
    )
    parser.add_argument(
        "--secret",
        type=str,
        default=os.getenv("JWT_SECRET"),
        help="JWT secret (defaults to JWT_SECRET environment variable)",
    )

    args = parser.parse_args()

    secret = args.secret
    if not secret:
        print(
            "ERROR: JWT secret is required. Either set JWT_SECRET in .env or pass --secret <KEY>.",
            file=sys.stderr,
        )
        sys.exit(1)

    secret = secret.strip()
    if len(secret) < 32:
        print(
            f"ERROR: JWT secret must be at least 32 characters long. Received {len(secret)} characters.",
            file=sys.stderr,
        )
        sys.exit(1)

    for pattern in INSECURE_SECRET_PATTERNS:
        if pattern in secret.lower():
            print(
                f"ERROR: Insecure pattern '{pattern}' detected in JWT secret.",
                file=sys.stderr,
            )
            sys.exit(1)

    device_id = args.device_id.strip() if args.device_id else f"device-{uuid.uuid4().hex[:12]}"

    token = create_device_jwt(
        device_id=device_id,
        secret=secret,
        expires_in_minutes=args.expiry_minutes,
    )

    print("=" * 60)
    print("DEVICE JWT TOKEN GENERATED")
    print("=" * 60)
    print(f"Device ID:       {device_id}")
    print(f"Expiry Duration: {args.expiry_minutes} minutes")
    print(f"Token:\n{token}")
    print("=" * 60)
    print("Copy this token into the Android app Settings screen:")
    print("Settings -> JWT Session Token -> Paste token -> Save Settings")
    print("=" * 60)
    return token


if __name__ == "__main__":
    generate_token_cli()
