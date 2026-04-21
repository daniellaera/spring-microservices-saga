#!/bin/bash
# Creates an ADMIN user in the authdb
# Usage: ./scripts/create-admin.sh <email> <password>
# Requirements: Docker (no other tools needed)

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <email> <password>"
  exit 1
fi

EMAIL=$1
PASSWORD=$2

# Generate bcrypt hash using a Docker container (no local tools required)
HASH=$(docker run --rm httpd:alpine htpasswd -bnBC 10 "" "$PASSWORD" | tr -d ':\n' | sed 's/\$2y/\$2a/')

# Check if postgres container is running
if ! docker ps --format '{{.Names}}' | grep -q "^postgres$"; then
  echo "❌ Container 'postgres' is not running. Start it first:"
  echo "   docker compose -f docker-compose.local.yml up -d"
  exit 1
fi

# Insert into authdb
docker exec -i postgres psql -U testuser -d authdb <<EOF
INSERT INTO users (first_name, last_name, email, password, role, created_at)
VALUES ('Admin', 'User', '$EMAIL', '$HASH', 'ADMIN', NOW())
ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password, role = 'ADMIN';
EOF

echo "✅ Admin user '$EMAIL' created/updated"