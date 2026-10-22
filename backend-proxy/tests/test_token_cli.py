"""
Tests for administrative token generator utility.
"""

from auth import create_device_jwt, verify_device_jwt


def test_token_cli_logic(jwt_secret):
    device_id = "custom-handset-id"
    token = create_device_jwt(device_id, jwt_secret, expires_in_minutes=15)
    claims = verify_device_jwt(token, jwt_secret)
    assert claims["sub"] == device_id
    assert claims["exp"] > claims["iat"]
