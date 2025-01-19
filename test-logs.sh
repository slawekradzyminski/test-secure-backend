#!/bin/bash

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting application...${NC}"
mvn spring-boot:run > app.log 2>&1 & 
PID=$!

# Wait for the application to start
echo -e "${BLUE}Waiting for application to start...${NC}"
while ! nc -z localhost 4001; do   
  sleep 0.1
done
echo -e "${GREEN}Application started${NC}"

echo -e "\n${BLUE}Sending login request...${NC}"
TOKEN=$(curl -s -X POST http://localhost:4001/users/signin \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

echo -e "\n${BLUE}Sending users request...${NC}"
curl -s -X GET http://localhost:4001/users \
  -H "Authorization: Bearer ${TOKEN}"
echo

echo -e "\n${BLUE}Stopping application...${NC}"
kill $PID
wait $PID 2>/dev/null

echo -e "\n${BLUE}Request/Response Logs:${NC}"
echo "----------------------------------------"
awk '/\{/{p=1}p' app.log | grep -B 50 -A 50 '"type" : "request"' | grep -B 50 '"type" : "response"'
echo "----------------------------------------"

echo -e "\n${GREEN}Test completed${NC}"

# Cleanup
rm app.log 