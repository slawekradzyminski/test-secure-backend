#!/bin/bash
set -e

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

echo "Pulling Gemma model (this might take a while)..."
docker exec test-secure-backend-backend-1 curl -s -X POST http://ollama:11434/api/pull -d '{"model": "llama3.2:1b"}' | while read -r line; do
  if echo "$line" | grep -q '"status"'; then
    status=$(echo "$line" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
    if [ ! -z "$status" ]; then
      echo "Status: $status"
    fi
  fi
done

echo "Verifying model is available..."
MODEL_CHECK=$(docker exec test-secure-backend-backend-1 curl -s http://ollama:11434/api/tags | grep -o '"name":"llama3.2:1b"' || true)
if [ -z "$MODEL_CHECK" ]; then
  echo "Failed to pull Gemma model"
  docker compose down
  exit 1
fi
echo "Gemma model is ready!"

./test-ollama-endpoint.sh

# Check if login was successful by looking for token in response
if echo "$LOGIN_RESPONSE" | grep -q "token"; then
  echo "Verification successful!"
  exit 0
else
  echo "Verification failed! Login response did not contain token."
  exit 1
fi

