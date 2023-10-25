#!/bin/bash

# Starting the jar application
java -jar jwt-auth-service-1.0.0.jar &

# Function to check if port 4001 is operational
is_port_open() {
    echo > /dev/tcp/127.0.0.1/4001 2>/dev/null
}

# Wait for the application to be operational
TIMEOUT=120  # 2 minutes in seconds
INTERVAL=5   # Check every 5 seconds
ELAPSED=0

while ! is_port_open; do
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))

    if [ $ELAPSED -ge $TIMEOUT ]; then
        echo "Timeout waiting for the application to start on port 4001."
        exit 1
    fi

    echo "Waiting for the application to start on port 4001..."
done

echo "Application is operational on port 4001!"
