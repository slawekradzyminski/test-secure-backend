#!/bin/bash

# Start the application in the background
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &

# Save the PID of the process
APP_PID=$!

# Initialize elapsed time
ELAPSED_TIME=0

# Wait for the application to start
while true; do
  # If the elapsed time reaches 300 seconds (5 minutes), kill the process and exit
  if [ $ELAPSED_TIME -eq 300 ]; then
    echo "Application did not start within 5 minutes. Exiting."
    kill $APP_PID
    exit 1
  fi

  # Check if the application is up by making a request to the swagger UI
  RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" 'http://localhost:4001/swagger-ui/index.html')

  if [ $RESPONSE_CODE -eq 200 ]; then
    break
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