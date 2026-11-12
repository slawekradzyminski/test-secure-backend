#!/usr/bin/env bash
# Smoke test for the published (GraalVM native) backend image. The native image fixes its bean
# graph at build time, so this starts the same image under the production profiles (Artemis JMS,
# bootstrap admin) and under the local profile (local email outbox) and checks the paths that
# depend on runtime properties.
#   scripts/native-smoke.sh <image>
set -euo pipefail

IMAGE="${1:?usage: native-smoke.sh <image>}"
RUN_ID="native-smoke-$$"
NETWORK="$RUN_ID"
ADMIN_PASSWORD="SmokeAdminPassword-$(openssl rand -hex 8)"

cleanup() {
  docker rm -f "$RUN_ID-backend" "$RUN_ID-postgres" "$RUN_ID-artemis" "$RUN_ID-ollama" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  docker logs --tail 200 "$RUN_ID-backend" >&2 || true
  exit 1
}

wait_for_health() {
  for _ in $(seq 1 60); do
    if docker exec "$RUN_ID-backend" bash -ec 'exec 3<>/dev/tcp/127.0.0.1/4001; printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3; read -r _ status _ <&3; test "$status" = 200' 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  fail "backend did not become healthy"
}

# Runs curl inside the network against the backend and prints "<status> <body>".
call() {
  docker run --rm --network "$NETWORK" curlimages/curl:8.17.0 -s -w '\n%{http_code}' "$@" \
    | awk 'BEGIN{body=""} {lines[NR]=$0} END{for(i=1;i<NR;i++) body=body lines[i]; print lines[NR] " " body}'
}

start_backend() {
  docker rm -f "$RUN_ID-backend" >/dev/null 2>&1 || true
  docker run -d --name "$RUN_ID-backend" --network "$NETWORK" --network-alias backend "$@" "$IMAGE" >/dev/null
  wait_for_health
}

docker network create "$NETWORK" >/dev/null
docker run -d --name "$RUN_ID-postgres" --network "$NETWORK" --network-alias postgres \
  -e POSTGRES_DB=testdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  postgres:16.14-bookworm@sha256:92620daddcd947f8d5ab5ba66e848702fe443d87fed30c4cea8e389fd78dfc55 >/dev/null
docker run -d --name "$RUN_ID-artemis" --network "$NETWORK" --network-alias artemis \
  -e ARTEMIS_USER=admin -e ARTEMIS_PASSWORD=admin -e EXTRA_ARGS='--http-host 0.0.0.0 --relax-jolokia --no-autotune' \
  apache/artemis:2.55.0@sha256:a192eee9c46e6352625ca9dbf0beebe4a34c363111c628bbb10c4aff1227341d >/dev/null
docker run -d --name "$RUN_ID-ollama" --network "$NETWORK" --network-alias ollama-mock \
  slawekradzyminski/ollama-mock:1.0.11@sha256:2dc7b225ed7351e4dc60cbdd9cb7a817bcfb4d671ccb7551a0fe1969ff065a29 >/dev/null
for _ in $(seq 1 30); do
  docker exec "$RUN_ID-postgres" pg_isready -U postgres -d testdb >/dev/null 2>&1 && break
  sleep 1
done

echo "== production profiles (docker,server; Artemis; bootstrap admin)"
start_backend \
  -e SPRING_PROFILES_ACTIVE=docker,server -e SPRING_PROFILES_INCLUDE=graphql,grpc \
  -e SPRING_ARTEMIS_BROKER_URL=tcp://artemis:61616 -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/testdb \
  -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e JWT_SECRET_KEY="$(openssl rand -hex 32)" -e APP_BOOTSTRAP_ADMIN_ENABLED=true \
  -e APP_BOOTSTRAP_ADMIN_USERNAME=smokeadmin -e APP_BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  -e APP_BOOTSTRAP_ADMIN_EMAIL=smoke@example.test -e APP_BOOTSTRAP_PRODUCTS_ENABLED=true \
  -e APP_MFA_ENCRYPTION_PASSWORD="$(openssl rand -hex 24)" -e APP_MFA_ENCRYPTION_SALT="$(openssl rand -hex 16)" \
  -e OLLAMA_BASE_URL=http://ollama-mock:11434

signin=$(call -H 'Content-Type: application/json' -d "{\"username\":\"smokeadmin\",\"password\":\"$ADMIN_PASSWORD\"}" http://backend:4001/api/v1/users/signin)
[[ "$signin" == 200* ]] || fail "bootstrap admin sign-in: $signin"
token=$(sed -E 's/.*"token" *: *"([^"]+)".*/\1/' <<< "${signin#* }")
refresh=$(sed -E 's/.*"refreshToken" *: *"([^"]+)".*/\1/' <<< "${signin#* }")
[[ "$refresh" =~ ^[0-9a-f-]{36}$ ]] || fail "server profile must issue UUID refresh tokens: $refresh"
echo "bootstrap admin sign-in: ok"

# T-NATIVE-ARENA: the first Ollama calls of a fresh native process, fired concurrently, go through
# Reactor Netty's shared-arena allocation, which needs -H:+SharedArenaSupport.
for _ in $(seq 1 30); do
  [[ "$(call http://ollama-mock:11434/api/tags)" == 200* ]] && break
  sleep 1
done
chat_body='{"model":"qwen3.5:2b","messages":[{"role":"user","content":"Give me a quick status update on the Ollama mock"}]}'
pids=()
for i in 1 2 3 4; do
  docker run --rm --network "$NETWORK" curlimages/curl:8.17.0 -s -o /dev/null -w '%{http_code}' --max-time 60 \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$chat_body" \
    http://backend:4001/api/v1/ollama/chat > "/tmp/$RUN_ID-chat-$i" &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid" || true; done
for i in 1 2 3 4; do
  status=$(cat "/tmp/$RUN_ID-chat-$i" 2>/dev/null || echo none); rm -f "/tmp/$RUN_ID-chat-$i"
  [[ "$status" == 200 ]] || fail "concurrent ollama chat $i: $status"
done
docker logs "$RUN_ID-backend" 2>&1 | grep -q 'UnsupportedFeatureError' && fail "native image hit an UnsupportedFeatureError"
echo "concurrent ollama chat: ok"

products=$(call -H "Authorization: Bearer $token" http://backend:4001/api/v1/products)
[[ "$products" == 200* && "$products" == *'"name"'* ]] || fail "products: $products"
echo "bootstrap products: ok"

graphql=$(call -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"query":"{ products(limit: 1) { total items { name } } }"}' http://backend:4001/api/v1/graphql)
[[ "$graphql" == 200* && "$graphql" == *'"total"'* && "$graphql" != *'"errors"'* ]] || fail "graphql: $graphql"
echo "graphql: ok"

qr=$(docker run --rm --network "$NETWORK" curlimages/curl:8.17.0 -s -o /dev/null -w '%{http_code} %{content_type}' \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{"text":"native smoke"}' http://backend:4001/api/v1/qr/create)
[[ "$qr" == "200 image/png" ]] || fail "qr: $qr"
echo "qr: ok"

# Custom constraint validators are created reflectively; the request converter is strict about JSON types.
invalid=$(call -H 'Content-Type: application/json' \
  -d "{\"username\":\"    \",\"email\":\"smoke-invalid@example.test\",\"password\":\"$(printf 'p%.0s' {1..80})\",\"firstName\":\"Smoke\",\"lastName\":\"Tester\"}" \
  http://backend:4001/api/v1/users/signup | sed -E 's/" *: *"/":"/g')
[[ "$invalid" == 400* && "$invalid" == *'"username":"Username is required"'* \
  && "$invalid" == *'"password":"Maximum password length: 72 bytes"'* ]] || fail "signup validation: $invalid"
typed=$(call -H 'Content-Type: application/json' -d '{"username":"smoketyped","password":1234}' http://backend:4001/api/v1/users/signin | sed -E 's/" *: *"/":"/g')
[[ "$typed" == 400* && "$typed" == *'"password":"Must be a string"'* ]] || fail "strict JSON types: $typed"
issues=$(call -H "Authorization: Bearer $token" http://backend:4001/api/v1/admin/users/identity-issues)
[[ "$issues" == 200* ]] || fail "identity issues: $issues"
echo "validation and identity report: ok"

outbox=$(call -H "Authorization: Bearer $token" http://backend:4001/api/v1/local/email/outbox)
[[ "$outbox" == 404* ]] || fail "local outbox must be unavailable with the jms transport: $outbox"
echo "local outbox hidden: ok"

echo "== local profile (local email outbox, demo seed)"
docker exec "$RUN_ID-postgres" psql -U postgres -c 'drop database testdb with (force)' -c 'create database testdb' >/dev/null
start_backend \
  -e SPRING_PROFILES_ACTIVE=local -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/testdb \
  -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres

signin=$(call -H 'Content-Type: application/json' -d '{"username":"admin","password":"LocalDemoAdmin123!"}' http://backend:4001/api/v1/users/signin)
[[ "$signin" == 200* ]] || fail "seeded admin sign-in: $signin"
token=$(sed -E 's/.*"token" *: *"([^"]+)".*/\1/' <<< "${signin#* }")
refresh=$(sed -E 's/.*"refreshToken" *: *"([^"]+)".*/\1/' <<< "${signin#* }")
[[ "$refresh" =~ ^[A-Za-z0-9_-]{43}$ ]] || fail "local profile must issue 43-character refresh tokens: $refresh"
echo "seeded admin sign-in: ok"

outbox=$(call -H "Authorization: Bearer $token" http://backend:4001/api/v1/local/email/outbox)
[[ "$outbox" == 200* ]] || fail "local outbox: $outbox"
echo "local outbox available: ok"

# Event feed: checkout records order.created in domain_events; the feed serves it (after the
# settle delay) with the raw data JSON to the payment service token.
product_id=$(call -H "Authorization: Bearer $token" http://backend:4001/api/v1/products | grep -oE '"id" *: *[0-9]+' | head -1 | grep -oE '[0-9]+$' || true)
[[ "$product_id" =~ ^[0-9]+$ ]] || fail "product id for the event feed check: $product_id"
cart=$(call -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d "{\"productId\":$product_id,\"quantity\":1}" http://backend:4001/api/v1/cart/items)
[[ "$cart" == 200* ]] || fail "add to cart: $cart"
order=$(call -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"street":"1 Smoke Street","city":"Smoke City","state":"SC","zipCode":"12345","country":"USA"}' \
  http://backend:4001/api/v1/orders)
[[ "$order" == 201* ]] || fail "checkout: $order"
sleep 4
# The local profile indents JSON, so compare without spaces.
feed=$(call -H 'X-Service-Token: local-payment-token' 'http://backend:4001/internal/v1/events?types=order.created&limit=5' | tr -d ' ')
[[ "$feed" == 200* && "$feed" == *'"type":"order.created"'* && "$feed" == *'"data":{"orderId":'* \
  && "$feed" == *'"nextCursor":'* ]] || fail "event feed: $feed"
bad=$(call -H 'X-Service-Token: local-payment-token' 'http://backend:4001/internal/v1/events?limit=0')
[[ "$bad" == 400* ]] || fail "event feed must reject limit=0: $bad"
echo "event feed: ok"

echo "native smoke test passed"
