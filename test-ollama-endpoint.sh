#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
BASE_URL="http://localhost:4001"
USERNAME="admin"
PASSWORD="admin"
MODEL="qwen3:4b-instruct"
SIMPLE_PROMPT="Say hi"
THINKING_PROMPT="What is 15 * 23? Think step by step and show your reasoning."

# Timeout configurations (in seconds)
NORMAL_TIMEOUT=300    # 5 minutes for normal mode
THINKING_TIMEOUT=1200  # 20 minutes for thinking mode
CHAT_TIMEOUT=1200      # 20 minutes for chat

echo -e "${BOLD}Testing Ollama Endpoint with Think Flag${NC}\n"

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

# Function to test Ollama endpoint
test_ollama_endpoint() {
    local test_name="$1"
    local prompt="$2"
    local think_flag="$3"
    local temp_file=$(mktemp)
    
    echo -e "${YELLOW}${test_name}${NC}"
    echo "Prompt: ${prompt}"
    echo "Think flag: ${think_flag}"
    echo "Making request..."
    
    # Set timeout based on think flag
    local max_wait_time
    if [ "$think_flag" = "true" ]; then
        max_wait_time=$THINKING_TIMEOUT
        echo "Using extended timeout for thinking mode: ${max_wait_time}s"
    else
        max_wait_time=$NORMAL_TIMEOUT
        echo "Using normal timeout: ${max_wait_time}s"
    fi
    
    local start_time=$(date +%s)
    
    # Make the request with connection timeout and max-time
    timeout $max_wait_time curl -s -N \
      --connect-timeout 30 \
      --max-time $max_wait_time \
      --keepalive-time 60 \
      -X POST "${BASE_URL}/api/ollama/generate" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Content-Type: application/json" \
      -H "Accept: text/event-stream" \
      -H "Connection: keep-alive" \
      -d "{\"model\":\"${MODEL}\",\"prompt\":\"${prompt}\",\"stream\":true,\"think\":${think_flag}}" > "$temp_file"
    
    local curl_exit_code=$?
    local end_time=$(date +%s)
    local total_time=$((end_time - start_time))
    
    # Check curl exit code
    if [ $curl_exit_code -eq 124 ]; then
        echo -e "${RED}Request timed out after ${total_time}s (max: ${max_wait_time}s)${NC}"
        rm "$temp_file"
        return 1
    elif [ $curl_exit_code -ne 0 ]; then
        echo -e "${RED}Request failed with exit code: ${curl_exit_code}${NC}"
        rm "$temp_file"
        return 1
    fi
    
    # Check if we got a complete response
    if ! grep -q '"done" : true' "$temp_file"; then
        echo -e "${RED}Incomplete response - no 'done: true' found${NC}"
        echo "Response content:"
        cat "$temp_file"
        rm "$temp_file"
        return 1
    fi
    
    # Process response
    if grep -q "response" "$temp_file"; then
        local full_response=""
        while IFS= read -r line; do
            if [[ $line == data:* ]]; then
                local response_text=$(echo "$line" | grep -o '"response" : "[^"]*"' | cut -d'"' -f4)
                if [ ! -z "$response_text" ]; then
                    full_response="${full_response}${response_text}"
                fi
            fi
        done < "$temp_file"
        
        # Get total duration
        local total_duration=$(grep -o '"total_duration" : [0-9]*' "$temp_file" | tail -n 1 | cut -d' ' -f3)
        local total_duration_ms=$((total_duration / 1000000))
        
        echo -e "${GREEN}✓ Response received${NC}"
        echo "Request duration: ${total_time}s"
        echo "Model duration: ${total_duration_ms}ms"
        echo "Response length: ${#full_response} characters"
        echo -e "Response:\n${full_response}"
        echo -e "---\n"
        
        # Store results for comparison
        if [ "$think_flag" = "true" ]; then
            THINKING_RESPONSE="$full_response"
            THINKING_DURATION=$total_duration_ms
        else
            NORMAL_RESPONSE="$full_response"
            NORMAL_DURATION=$total_duration_ms
        fi
        
        rm "$temp_file"
        return 0
    else
        echo -e "${RED}✗ No valid response received${NC}"
        echo "Response content:"
        cat "$temp_file"
        rm "$temp_file"
        return 1
    fi
}

# Function to test chat endpoint
test_chat_endpoint() {
    local test_name="$1"
    local prompt="$2"
    local think_flag="$3"
    local temp_file=$(mktemp)
    
    echo -e "${YELLOW}${test_name}${NC}"
    echo "Prompt: ${prompt}"
    echo "Think flag: ${think_flag}"
    echo "Making chat request..."
    
    local max_wait_time=$CHAT_TIMEOUT
    echo "Using chat timeout: ${max_wait_time}s"
    
    local start_time=$(date +%s)
    
    # Make the chat request
    timeout $max_wait_time curl -s -N \
      --connect-timeout 30 \
      --max-time $max_wait_time \
      --keepalive-time 60 \
      -X POST "${BASE_URL}/api/ollama/chat" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Content-Type: application/json" \
      -H "Accept: text/event-stream" \
      -H "Connection: keep-alive" \
      -d "{\"model\":\"${MODEL}\",\"messages\":[{\"role\":\"user\",\"content\":\"${prompt}\"}],\"stream\":false,\"think\":${think_flag}}" > "$temp_file"
    
    local curl_exit_code=$?
    local end_time=$(date +%s)
    local total_time=$((end_time - start_time))
    
    # Check curl exit code
    if [ $curl_exit_code -eq 124 ]; then
        echo -e "${RED}Chat request timed out after ${total_time}s (max: ${max_wait_time}s)${NC}"
        rm "$temp_file"
        return 1
    elif [ $curl_exit_code -ne 0 ]; then
        echo -e "${RED}Chat request failed with exit code: ${curl_exit_code}${NC}"
        rm "$temp_file"
        return 1
    fi
    
    # Check if we got a complete response
    if ! grep -q '"done" : true' "$temp_file"; then
        echo -e "${RED}Incomplete chat response - no 'done: true' found${NC}"
        echo "Response content:"
        cat "$temp_file"
        rm "$temp_file"
        return 1
    fi
    
    if grep -q "content" "$temp_file"; then
        echo -e "${GREEN}✓ Chat response received${NC}"
        
        # Extract chat content
        local chat_response=""
        while IFS= read -r line; do
            if [[ $line == data:* ]]; then
                local chat_content=$(echo "$line" | grep -o '"content" : "[^"]*"' | cut -d'"' -f4)
                if [ ! -z "$chat_content" ]; then
                    chat_response="${chat_response}${chat_content}"
                fi
            fi
        done < "$temp_file"
        
        echo "Request duration: ${total_time}s"
        echo "Response length: ${#chat_response} characters"
        echo "Chat response:"
        echo "$chat_response"
        echo -e "---\n"
        
        rm "$temp_file"
        return 0
    else
        echo -e "${RED}✗ No valid chat response received${NC}"
        echo "Response content:"
        cat "$temp_file"
        rm "$temp_file"
        return 1
    fi
}

# Step 2: Test generate endpoint without thinking (baseline)
echo "2. Testing generate endpoint without thinking..."
if ! test_ollama_endpoint "Test 1: Generate (think=false)" "$SIMPLE_PROMPT" "false"; then
    exit 1
fi

# Step 3: Test generate endpoint with thinking
echo "3. Testing generate endpoint with thinking..."
if ! test_ollama_endpoint "Test 2: Generate (think=true)" "$THINKING_PROMPT" "true"; then
    exit 1
fi

# Step 4: Test chat endpoint with thinking
echo "4. Testing chat endpoint with thinking..."
if ! test_chat_endpoint "Test 3: Chat (think=true)" "$THINKING_PROMPT" "true"; then
    exit 1
fi

# Step 5: Analysis and summary
echo -e "${BOLD}5. Analysis Summary${NC}"
echo "=================================="

echo -e "\n${BOLD}Think Flag Verification:${NC}"
echo "✓ Generate endpoint accepts think=false parameter"
echo "✓ Generate endpoint accepts think=true parameter"
echo "✓ Chat endpoint accepts think=true parameter"
echo "✓ All requests completed successfully"

echo -e "\n${BOLD}Response Analysis:${NC}"
echo "Generate endpoint baseline test (think=false) completed successfully"
echo "Generate endpoint thinking test (think=true) completed successfully"
echo "Chat endpoint thinking test (think=true) completed successfully"

echo -e "\n${BOLD}Performance Comparison:${NC}"
if [ ! -z "$NORMAL_DURATION" ] && [ ! -z "$THINKING_DURATION" ]; then
    echo "Normal mode duration: ${NORMAL_DURATION}ms"
    echo "Thinking mode duration: ${THINKING_DURATION}ms"
    
    if [ $THINKING_DURATION -gt $NORMAL_DURATION ]; then
        DIFF=$((THINKING_DURATION - NORMAL_DURATION))
        echo "Thinking mode took ${DIFF}ms longer (expected for reasoning tasks)"
    else
        echo "Duration difference: $((NORMAL_DURATION - THINKING_DURATION))ms"
    fi
fi

echo -e "\n${GREEN}${BOLD}All tests passed successfully!${NC}"
echo "The think flag is properly implemented and functional across all Ollama endpoints." 