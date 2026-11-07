#!/bin/sh
set -e

# Load .env, then put back anything already set by `docker run --env-file` or -e.
if [ -f /app/.env ]; then
  saved=$(mktemp)
  export -p > "$saved"
  set -a
  # shellcheck disable=SC1091
  . /app/.env
  set +a
  # shellcheck disable=SC1090
  . "$saved"
  rm -f "$saved"
fi

exec java $JAVA_OPTS -jar /app/app.jar
