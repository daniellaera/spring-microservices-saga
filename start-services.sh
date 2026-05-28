#!/bin/bash

BASE_DIR=$(pwd)
RESET="\033[0m"
DOCKER_FILE="docker-compose.local.yml"

# --- Cleanup Function ---
cleanup() {
  # Save the exit status of whatever triggered the cleanup
  local exit_status=$?

  echo -e "\n\033[1;31mStopping Docker containers (Keeping data intact)...\033[0m"
  # 'stop' pauses the containers without removing them or deleting volumes
  docker compose -f "$DOCKER_FILE" stop 2>/dev/null || true

  echo -e "\033[1;31mTerminating all background service logs...\033[0m"

  # Clear the trap so we don't accidentally trigger an infinite loop during exit
  trap - EXIT INT TERM

  # Kill the entire process group (-$$) to instantly stop all background subshell log loops
  kill -9 -$$ 2>/dev/null || true

  exit $exit_status
}

# Trap Ctrl+C (SIGINT), SIGTERM, and normal script exit to trigger the cleanup
trap cleanup EXIT INT TERM

get_color() {
  case $1 in
    "config-server")       echo "\033[0;36m" ;;  # cyan
    "auth-service")        echo "\033[0;35m" ;;  # purple
    "order-service")       echo "\033[0;32m" ;;  # green
    "inventory-service")   echo "\033[0;33m" ;;  # yellow
    "payment-service")     echo "\033[0;34m" ;;  # blue
    "notification-service") echo "\033[0;31m" ;; # red
    "gateway-service")     echo "\033[0;37m" ;;  # white
    "shop-ui")             echo "\033[0;95m" ;; # magenta
    *)                     echo "\033[0m" ;;
  esac
}

# --- 1. Start Docker Infrastructure ---
echo -e "\033[1;34mStarting Docker containers from $DOCKER_FILE...\033[0m"
if [ -f "$DOCKER_FILE" ]; then
  # Simply run 'up -d'. Docker will reuse existing stopped containers without recreating them
  docker compose -f "$DOCKER_FILE" up -d
else
  echo -e "\033[1;31mError: $DOCKER_FILE not found!\033[0m"
  exit 1
fi

# Give Docker services a moment to spin up
sleep 3

# --- 2. Kill Existing Local Services ---
echo "Killing existing local services running on ports..."
for port in 8888 8080 8081 8082 8083 8084 8085 4200; do
  kill -9 $(lsof -t -i:$port) 2>/dev/null || true
done

start_service() {
  service=$1
  profile=$2  # optional Spring profile, e.g. "local"
  color=$(get_color "$service")

  echo -e "${color}Starting $service...${RESET}"

  (
    cd "$BASE_DIR/$service" || exit 1
    if [ -n "$profile" ]; then
      mvn spring-boot:run -Dspring-boot.run.profiles="$profile" 2>&1 | while IFS= read -r line; do
        echo -e "${color}[$service]${RESET} $line"
      done
    else
      mvn spring-boot:run 2>&1 | while IFS= read -r line; do
        echo -e "${color}[$service]${RESET} $line"
      done
    fi
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
start_service "auth-service" "local"
wait_for_service "http://localhost:8084" "auth-service"
sleep 2

# Start business services in parallel
start_service "order-service" "local"
start_service "inventory-service" "local"
start_service "payment-service" "local"
start_service "notification-service" "local"

wait_for_service "http://localhost:8081" "order-service"
wait_for_service "http://localhost:8082" "inventory-service"
wait_for_service "http://localhost:8083" "payment-service"
wait_for_service "http://localhost:8085" "notification-service"
sleep 2

# Start gateway last
start_service "gateway-service" "local"
wait_for_service "http://localhost:8080" "gateway-service"
sleep 2

# Start Angular UI last
color=$(get_color "shop-ui")
echo -e "${color}Starting shop-ui...${RESET}"
(
  cd "$BASE_DIR/shop-ui" || exit 1
  npx ng serve 2>&1 | while IFS= read -r line; do
    echo -e "${color}[shop-ui]${RESET} $line"
  done
) &

echo -e "\033[1;32mAll services started successfully! 🎉\033[0m"
echo -e "\033[1;32mUI available at: http://localhost:4200\033[0m"

# Keep script running to show logs.
wait