#!/bin/bash

# Set up Docker buildx
docker buildx create --name mybuilder --use || true
docker buildx inspect --bootstrap

# Build and push multi-architecture image
docker buildx build --platform linux/amd64,linux/arm64 \
  -t slawekradzyminski/backend:2.7.0 \
  --push \
  .

echo "Multi-architecture image built and pushed successfully!"
echo "You can verify the image with: docker buildx imagetools inspect slawekradzyminski/backend:2.7.0"