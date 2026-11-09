(
  set -eo pipefail
  base=http://localhost:8081
  cookies=$(mktemp)
  trap 'rm -f "$cookies"' EXIT

  # Get the CSRF cookie required by the login POST.
  # echo 'GET CSRF cookie'
  curl --fail-with-body -sS -i -c "$cookies" "$base/api/v1/auth/csrf" -o /dev/null
  csrf=$(awk '$6 == "XSRF-TOKEN" { print $7; exit }' "$cookies")
  if [ -z "$csrf" ]; then
    echo "The backend did not issue an XSRF-TOKEN cookie." >&2
    exit 1
  fi

  # Log in and save the CHESS_SESSION cookie.
  # echo 'POST login with credentials and CSRF token' >&2
  python3 -c '
import getpass
import json
import sys

email = "lr_1793948057587_37179_1@example.com"
password = "LoadTestOnly-123!"

if not email.strip():
    print("Email: ", end="", file=sys.stderr, flush=True)
    email = input().strip()

if not password:
    password = getpass.getpass("Password: ")
print(json.dumps({"email": email.strip(), "password": password}))
' | curl --fail-with-body -sS \
    -b "$cookies" -c "$cookies" \
    -H "X-XSRF-TOKEN: $csrf" \
    -H 'Content-Type: application/json' \
    --data-binary @- \
    --write-out '\nHTTP %{http_code}\n' \
    "$base/api/v1/auth/login"
curl -fsS -b "$cookies" "$base/actuator/metrics/executor.active"
printf '\n'
  # Read Actuator with the authenticated session.
  # echo 'GET Actuator metrics with authenticated session'
  # curl --fail-with-body -sS -i -b "$cookies" "$base/actuator/metrics"
  while true; do
  printf '\n%s\n' "$(date -Iseconds)"

  for metric in \
    chess.ws.outbound.dispatch.wait \
    chess.ws.outbound.session.validation
  do
    printf '%s\n' "$metric"
    curl -fsS -b "$cookies" "$base/actuator/metrics/$metric"
    printf '\n'
  done
  for metric in \
    executor.active \
    executor.queued \
    executor.pool.size \
    executor.pool.core \
    executor.pool.max
  do
    curl -fsS -b "$cookies" --get \
      --data-urlencode 'tag=name:clientOutboundChannelExecutor' \
      "$base/actuator/metrics/$metric"
    printf '\n'
  done

  sleep 4
done
  printf '\n'
)