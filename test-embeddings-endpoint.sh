#!/bin/bash

# Get a JWT token first
echo "Getting JWT token..."
TOKEN=$(curl -s -X POST http://localhost:4001/users/signin \
  -H "Content-Type: application/json" \
  -d '{"username": "client", "password": "client"}' | jq -r '.token')

if [ -z "$TOKEN" ]; then
  echo "Failed to get token. Make sure the backend is running."
  exit 1
fi

echo "Token obtained successfully."

# Test the embeddings endpoint
echo "Testing embeddings endpoint..."
curl -X POST http://localhost:4001/api/embeddings/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "text": "Hello world",
    "modelName": "gpt2",
    "dimensionalityReduction": true,
    "reductionMethod": "pca",
    "nComponents": 2
  }'

echo -e "\n\nDone!" 