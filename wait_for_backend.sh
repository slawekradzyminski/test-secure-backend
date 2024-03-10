#!/bin/bash
echo "Waiting for port 4001 to be active..."

# Function to check if port is open
wait_for_port() {
  local port=$1
  local timeout=$2
  local start_time=$(date +%s)

  while :; do
    (echo > /dev/tcp/localhost/$port) >/dev/null 2>&1
    local result=$?
    if [[ $result -eq 0 ]]; then
      echo "Port $port is active."
      return 0
    else
      local current_time=$(date +%s)
      local elapsed=$((current_time - start_time))

      if [[ $elapsed -ge $timeout ]]; then
        echo "Timeout reached. Port $port did not become active within $timeout seconds."
        return 1
      fi
    fi
    sleep 5
  done
}

# Use the wait_for_port function with a 120-second timeout
if ! wait_for_port 4001 120; then
  exit 1
fi

# Step 4: Continue with the rest of the script
echo "Port 4001 is now active. Proceeding with the rest of the script..."