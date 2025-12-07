#!/bin/bash
set -euo pipefail

echo "Starting Docker environment..."
nohup docker compose up --build -d > docker.log 2>&1 &

echo "Waiting for application to start (max 5 minutes)..."
TIMEOUT=300  # 5 minutes in seconds
INTERVAL=10  # polling interval in seconds
ELAPSED=0

while [ $ELAPSED -lt $TIMEOUT ]; do
  if curl -s "http://localhost:4001/v3/api-docs" > /dev/null; then
    echo "Application is up and running!"
    break
  fi
  echo "Waiting for application to start... ($ELAPSED seconds elapsed)"
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done

if [ $ELAPSED -ge $TIMEOUT ]; then
  echo "Timeout waiting for application to start"
  docker compose down
  exit 1
fi

# Generate random username
RANDOM_USER="user_$(date +%s)"

echo "Attempting to register new user..."
REGISTER_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"password123\",\"email\":\"$RANDOM_USER@test.com\",\"firstName\":\"John\",\"lastName\":\"Boyd\",\"roles\":[\"ROLE_CLIENT\"]}" \
  http://localhost:4001/users/signup)

echo "Register response: $REGISTER_RESPONSE"

echo "Attempting to login..."
LOGIN_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"username\":\"$RANDOM_USER\",\"password\":\"password123\"}" \
  http://localhost:4001/users/signin)

echo "Login response: $LOGIN_RESPONSE"

echo "Pulling LLM model (this might take a while)..."
curl -s -X POST http://localhost:11434/api/pull -d '{"model": "qwen3:4b-instruct"}' | while read -r line; do
  if echo "$line" | grep -q '"status"'; then
    status=$(echo "$line" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
    if [ ! -z "$status" ]; then
      echo "Status: $status"
    fi
  fi
done

echo "Verifying model is available..."
MODEL_CHECK=$(curl -s http://localhost:11434/api/tags | grep -o '"name":"qwen3:4b-instruct"' || true)
if [ -z "$MODEL_CHECK" ]; then
  echo "Failed to pull LLM"
  docker compose down
  exit 1
fi
echo "LLM is ready!"

TOKEN=$(echo "$LOGIN_RESPONSE" | sed -n 's/.*"token" : "\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "Failed to extract token from login response."
  docker compose down
  exit 1
fi

echo "Testing Ollama generate endpoint without thinking..."
GENERATE_RESPONSE=$(curl -s -X POST http://localhost:4001/api/ollama/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen3:4b-instruct","prompt":"Say hi","stream":false,"think":false}')

echo "Generate response: $GENERATE_RESPONSE"

if ! echo "$GENERATE_RESPONSE" | grep -q '"done" : true'; then
  echo "Generate endpoint verification failed."
  docker compose down
  exit 1
fi

# Check if login was successful by looking for token in response
if echo "$LOGIN_RESPONSE" | grep -q "token"; then
  echo "Verification successful!"
  exit 0
else
  echo "Verification failed! Login response did not contain token."
  exit 1
fi
