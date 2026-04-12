#!/bin/bash

BASE_DIR=$(pwd)
RESET="\033[0m"

get_color() {
  case $1 in
    "config-server")       echo "\033[0;36m" ;;  # cyan
    "auth-service")        echo "\033[0;35m" ;;  # purple
    "order-service")       echo "\033[0;32m" ;;  # green
    "inventory-service")   echo "\033[0;33m" ;;  # yellow
    "payment-service")     echo "\033[0;34m" ;;  # blue
    "notification-service") echo "\033[0;31m" ;; # red
    "gateway-service")     echo "\033[0;37m" ;;  # white
    *)                     echo "\033[0m" ;;
  esac
}

echo "Killing existing services..."
for port in 8888 8080 8081 8082 8083 8084 8085; do
  kill -9 $(lsof -t -i:$port) 2>/dev/null || true
done

start_service() {
  service=$1
  color=$(get_color "$service")

  echo -e "${color}Starting $service...${RESET}"

  (
    cd "$BASE_DIR/$service" || exit 1
    mvn spring-boot:run 2>&1 | while IFS= read -r line; do
      echo -e "${color}[$service]${RESET} $line"
    done
  ) &
}

wait_for_service() {
  url=$1
  name=$2
  color=$(get_color "$name")

  echo -e "${color}Waiting for $name...${RESET}"

  until curl -s "$url/actuator/health" | grep -q UP; do
    sleep 2
  done

  echo -e "${color}$name is UP ✅${RESET}"
}

# Start config-server first
start_service "config-server"
wait_for_service "http://localhost:8888" "config-server"
sleep 3

# Start auth-service
start_service "auth-service"
wait_for_service "http://localhost:8084" "auth-service"
sleep 2

# Start business services in parallel
start_service "order-service"
start_service "inventory-service"
start_service "payment-service"
start_service "notification-service"

wait_for_service "http://localhost:8081" "order-service"
wait_for_service "http://localhost:8082" "inventory-service"
wait_for_service "http://localhost:8083" "payment-service"
wait_for_service "http://localhost:8085" "notification-service"
sleep 2

# Start gateway last
start_service "gateway-service"
wait_for_service "http://localhost:8080" "gateway-service"

echo -e "\033[1;32mAll services started successfully! 🎉\033[0m"

# Keep script running to show logs
wait