#!/bin/bash

# Script to build and push all microservices to Docker Hub (multi-arch)

DOCKERHUB_USERNAME="daniellaera"
TAG="latest"

# List of services
SERVICES=("config-server" "order-service" "inventory-service" "payment-service" "gateway-service")

# Loop over each service
for SERVICE in "${SERVICES[@]}"; do
  IMAGE_NAME="$DOCKERHUB_USERNAME/$SERVICE:$TAG"
  SERVICE_DIR="./$SERVICE"

  echo "🔧 Building multi-arch Docker image for $SERVICE -> $IMAGE_NAME"

  docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t "$IMAGE_NAME" \
    -f "$SERVICE_DIR/Dockerfile" \
    . \
    --push

  if [ $? -ne 0 ]; then
    echo "❌ Failed to build or push image for $SERVICE"
    exit 1
  fi

  echo "✅ $SERVICE image pushed: $IMAGE_NAME"
done

echo "🎉 All services have been built and pushed successfully!"