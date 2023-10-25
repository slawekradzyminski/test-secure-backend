#!/bin/bash

# Start the jar application
java -jar ./jwt-auth-service-1.0.0.jar &

# Counter for the waiting time
counter=0

# Wait for the port 4001 to be up, with a timeout of 180 seconds
while ! nc -z localhost 4001; do
  sleep 1
  ((counter++))
  if [ $counter -gt 180 ]; then
    echo "Application failed to start after 120 seconds, exiting."
    exit 1
  fi
  echo "Waiting for application to start... ($counter seconds)"
done

echo "Application successfully started on port 4001."
