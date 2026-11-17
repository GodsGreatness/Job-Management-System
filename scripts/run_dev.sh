#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Starting Java Spring Boot backend on http://localhost:8080 ..."
(cd "$ROOT_DIR/backend" && chmod +x mvnw && ./mvnw spring-boot:run) &
BACK_PID=$!

echo "Starting React Vite frontend on http://localhost:5173 ..."
(cd "$ROOT_DIR/frontend" && npm install && npm run dev) &
FRONT_PID=$!

trap 'kill $BACK_PID $FRONT_PID 2>/dev/null || true' EXIT
wait
