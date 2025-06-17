#!/bin/bash

# Exit if no version is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION="$1"

# Set up Docker buildx
docker buildx create --name mybuilder --use || true
docker buildx inspect --bootstrap

# Build and push multi-architecture image
docker buildx build --platform linux/amd64,linux/arm64 \
  -t slawekradzyminski/backend:$VERSION \
  --push \
  .

echo "Multi-architecture image built and pushed successfully!"
echo "You can verify the image with: docker buildx imagetools inspect slawekradzyminski/backend:$VERSION"
