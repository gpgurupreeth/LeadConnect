#!/usr/bin/env bash
# WAHA lifecycle helper. Run inside WSL as root:
#   wsl -u root -e bash /mnt/c/.../backend-proxy/waha/waha.sh status
set -euo pipefail

cd "$(dirname "$0")"

ACTION="${1:-status}"
SESSION="${2:-default}"
BASE_URL="http://127.0.0.1:3000"

load_env() {
  if [[ ! -f .env ]]; then
    echo "ERROR: backend-proxy/waha/.env is missing. Copy .env.example and set secrets." >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
  if [[ -z "${WAHA_API_KEY:-}" || -z "${WAHA_DASHBOARD_PASSWORD:-}" ]]; then
    echo "ERROR: WAHA_API_KEY and WAHA_DASHBOARD_PASSWORD must be set in .env." >&2
    exit 1
  fi
}

api() {
  curl -sS -H "X-Api-Key: ${WAHA_API_KEY}" "$@"
}

case "$ACTION" in
  up)
    docker compose up -d
    docker compose ps
    ;;
  down)
    docker compose down
    ;;
  restart)
    docker compose restart
    docker compose ps
    ;;
  status)
    docker compose ps
    docker compose logs --tail=40
    ;;
  logs)
    docker compose logs -f --tail=100
    ;;
  wait-health)
    load_env
    for i in $(seq 1 40); do
      code=$(curl -sS -o /dev/null -w '%{http_code}' -H "X-Api-Key: ${WAHA_API_KEY}" "${BASE_URL}/health" || true)
      echo "attempt=${i} health_http=${code}"
      if [[ "$code" == "200" ]]; then
        exit 0
      fi
      sleep 3
    done
    echo "WAHA did not become healthy within 120 seconds." >&2
    exit 1
    ;;
  health)
    load_env
    echo "health_without_key_http=$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/health")"
    echo "health_with_key_http=$(curl -sS -o /dev/null -w '%{http_code}' -H "X-Api-Key: ${WAHA_API_KEY}" "${BASE_URL}/health")"
    echo "auth_without_key_http=$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/api/sessions")"
    echo "auth_with_key_http=$(curl -sS -o /dev/null -w '%{http_code}' -H "X-Api-Key: ${WAHA_API_KEY}" "${BASE_URL}/api/sessions")"
    echo -n "session=${SESSION} "
    api "${BASE_URL}/api/sessions/${SESSION}" || true
    echo
    ;;
  start-session)
    load_env
    status_code=$(curl -sS -o /tmp/waha-session-${SESSION}.json -w '%{http_code}' \
      -H "X-Api-Key: ${WAHA_API_KEY}" "${BASE_URL}/api/sessions/${SESSION}")
    if [[ "$status_code" == "200" ]]; then
      api -X POST "${BASE_URL}/api/sessions/${SESSION}/start" || true
    else
      api -X POST -H "Content-Type: application/json" \
        -d "{\"name\":\"${SESSION}\",\"start\":true}" \
        "${BASE_URL}/api/sessions" || true
    fi
    echo
    ;;
  restart-session)
    load_env
    api -X POST "${BASE_URL}/api/sessions/${SESSION}/restart" || true
    echo
    ;;
  qr)
    load_env
    out="./qr-${SESSION}.png"
    api "${BASE_URL}/api/${SESSION}/auth/qr?format=image" -o "$out"
    if ! file "$out" | grep -q 'PNG image data'; then
      echo "ERROR: WAHA did not return a PNG QR image. Response:" >&2
      cat "$out" >&2
      echo >&2
      exit 1
    fi
    echo "QR image saved to: $(wslpath -w "$out")"
    echo "Scan it in WhatsApp on the dedicated test phone, then run:"
    echo "  .\\manage-waha.ps1 -Action health -Session ${SESSION}"
    ;;
  fresh-qr)
    load_env
    api -X POST "${BASE_URL}/api/sessions/${SESSION}/restart" >/dev/null || true
    status=""
    for i in $(seq 1 30); do
      status=$(api "${BASE_URL}/api/sessions/${SESSION}" \
        | grep -o '"status":"[^"]*"' \
        | head -1 \
        | cut -d'"' -f4 || true)
      echo "session_status=${status}"
      if [[ "$status" == "SCAN_QR_CODE" ]]; then
        break
      fi
      sleep 1
    done
    if [[ "$status" != "SCAN_QR_CODE" ]]; then
      echo "ERROR: session did not reach SCAN_QR_CODE; status=${status}" >&2
      exit 1
    fi
    out="./qr-${SESSION}.png"
    api "${BASE_URL}/api/${SESSION}/auth/qr?format=image" -o "$out"
    if ! file "$out" | grep -q 'PNG image data'; then
      echo "ERROR: WAHA did not return a PNG QR image. Response:" >&2
      cat "$out" >&2
      echo >&2
      exit 1
    fi
    echo "Fresh QR image saved to: $(wslpath -w "$out")"
    echo "Scan it now with WhatsApp -> Linked devices on the dedicated test phone."
    echo "Then run: .\\manage-waha.ps1 -Action health -Session ${SESSION}"
    ;;
  *)
    echo "Usage: $0 {up|down|restart|status|logs|wait-health|health|start-session|restart-session|qr|fresh-qr} [session]" >&2
    exit 2
    ;;
esac
