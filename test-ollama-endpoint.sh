#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
BASE_URL="http://localhost:4001"
USERNAME="admin"
PASSWORD="admin"
MODEL="gemma:2b"
PROMPT="Say hi"

echo -e "${BOLD}Testing Ollama Endpoint${NC}\n"

# Step 1: Get JWT token
echo "1. Getting JWT token..."
TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/users/signin" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")

# Extract token using grep and cut
TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token" : "[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Failed to get token. Response: ${TOKEN_RESPONSE}${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Token received${NC}\n"

# Step 2: Test Ollama endpoint
echo "2. Testing Ollama endpoint..."
echo "Making request to generate text..."

# Use temporary files to store the response and concatenated text
TEMP_FILE=$(mktemp)
FULL_RESPONSE=""
MAX_WAIT_TIME=30  # Maximum wait time in seconds
START_TIME=$(date +%s)

# Make the request and capture the response
curl -s -N -X POST "${BASE_URL}/api/ollama/generate" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "{\"model\":\"${MODEL}\",\"prompt\":\"${PROMPT}\",\"stream\":true}" > "$TEMP_FILE" &

CURL_PID=$!

# Function to extract response text from a line
extract_response() {
    echo "$1" | grep -o '"response" : "[^"]*"' | cut -d'"' -f4
}

# Wait for streaming to complete or timeout
echo "Waiting for response (timeout: ${MAX_WAIT_TIME}s)..."
DONE=false
while [ "$DONE" = false ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED_TIME=$((CURRENT_TIME - START_TIME))
    
    if [ $ELAPSED_TIME -gt $MAX_WAIT_TIME ]; then
        echo -e "${RED}Timeout waiting for response${NC}"
        kill $CURL_PID 2>/dev/null
        rm "$TEMP_FILE"
        exit 1
    fi

    if grep -q '"done" : true' "$TEMP_FILE"; then
        DONE=true
    else
        sleep 1
    fi
done

# Kill the curl process
kill $CURL_PID 2>/dev/null

# Check if we got a valid response
if grep -q "response" "$TEMP_FILE"; then
    echo -e "${GREEN}✓ Received complete streaming response${NC}"
    
    # Process the response and concatenate all text
    while IFS= read -r line; do
        if [[ $line == data:* ]]; then
            RESPONSE_TEXT=$(extract_response "$line")
            if [ ! -z "$RESPONSE_TEXT" ]; then
                FULL_RESPONSE="${FULL_RESPONSE}${RESPONSE_TEXT}"
            fi
        fi
    done < "$TEMP_FILE"
    
    # Get total duration from the last response
    TOTAL_DURATION=$(grep -o '"total_duration" : [0-9]*' "$TEMP_FILE" | tail -n 1 | cut -d' ' -f3)
    TOTAL_DURATION_MS=$((TOTAL_DURATION / 1000000)) # Convert nanoseconds to milliseconds
    
    echo -e "\n${BOLD}Complete Response:${NC}"
    echo -e "Prompt: ${PROMPT}"
    echo -e "Model: ${MODEL}"
    echo -e "Duration: ${TOTAL_DURATION_MS}ms"
    echo -e "Response:\n${FULL_RESPONSE}"
else
    echo -e "${RED}✗ No valid response received${NC}"
    echo "Response content:"
    cat "$TEMP_FILE"
    rm "$TEMP_FILE"
    exit 1
fi

# Clean up
rm "$TEMP_FILE"

echo -e "\n${GREEN}${BOLD}All tests passed successfully!${NC}" 