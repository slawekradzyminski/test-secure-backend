#!/bin/bash
echo "Running Java application..."

java -jar target/jwt-auth-service-1.0.0.jar &

# Use the existing wait_for_backend.sh script to wait for port 4001
./wait_for_backend.sh

if [ $? -ne 0 ]; then
  echo "Java application failed to start properly."
  exit 1
fi

echo "Java application is running and port 4001 is active."