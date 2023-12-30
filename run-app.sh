#!/bin/bash

# Start the application in the background
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &

# Save the PID of the process
APP_PID=$!

# Initialize elapsed time
ELAPSED_TIME=0

# Wait for the application to start
while ! nc -z localhost 4001; do
  # If the elapsed time reaches 300 seconds (5 minutes), kill the process and exit
  if [ $ELAPSED_TIME -eq 300 ]; then
    echo "Application did not start within 5 minutes. Exiting."
    kill $APP_PID
    exit 1
  fi

  # Sleep for 1 second
  sleep 1

  # Increase the elapsed time
  ELAPSED_TIME=$((ELAPSED_TIME+1))

  # Print the elapsed time every 10 seconds
  if [ $((ELAPSED_TIME%10)) -eq 0 ]; then
    echo "Waiting for application to start. Elapsed time: $ELAPSED_TIME seconds."
  fi
done

echo "Application started successfully."