#!/bin/sh
set -e

# Load .env, then put back non-empty values already set by `docker run --env-file` or -e.
# A blank injected variable must not wipe the value from .env.

strip_quotes() {
  v=$1
  case $v in
    \"*\") v=${v#\"}; v=${v%\"} ;;
    \'*\') v=${v#\'}; v=${v%\'} ;;
  esac
  printf '%s' "$v"
}

KEYS="SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD SPRING_MAIL_FROM MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_BUCKET MINIO_PUBLIC_URL CORS_ORIGINS SITE_URL"

if [ -f /app/.env ]; then
  injected=$(mktemp)
  for key in $KEYS; do
    eval "raw=\${$key-}"
    [ -n "$raw" ] || continue
    printf '%s=%s\n' "$key" "$raw" >> "$injected"
  done
  set -a
  # shellcheck disable=SC1091
  . /app/.env
  set +a
  while IFS= read -r line || [ -n "$line" ]; do
    key=${line%%=*}
    val=$(strip_quotes "${line#*=}")
    [ -n "$val" ] || continue
    export "$key=$val"
  done < "$injected"
  rm -f "$injected"
fi

for key in $KEYS; do
  eval "raw=\${$key-}"
  [ -n "$raw" ] || continue
  cleaned=$(strip_quotes "$raw")
  if [ "$cleaned" != "$raw" ]; then
    export "$key=$cleaned"
  fi
done

reachable() {
  timeout 3 bash -c "echo >/dev/tcp/$1/$2" >/dev/null 2>&1
}

# From inside Docker, the server's own public IP is often unreachable (hairpin / firewall).
# Postgres on this host is reachable through the container's default gateway instead.
url=${SPRING_DATASOURCE_URL:-}
case $url in
  jdbc:postgresql://*)
    rest=${url#jdbc:postgresql://}
    hostport=${rest%%/*}
    host=${hostport%%:*}
    port=${hostport#*:}
    if [ "$port" = "$hostport" ]; then
      port=5432
    fi
    if [ -f /.dockerenv ] && ! reachable "$host" "$port"; then
      gw_hex=$(awk '$2 == "00000000" { print $3; exit }' /proc/net/route 2>/dev/null || true)
      gw=
      if [ -n "$gw_hex" ] && [ "$gw_hex" != "00000000" ]; then
        a=$(printf '%d' "0x$(printf '%s' "$gw_hex" | cut -c7-8)")
        b=$(printf '%d' "0x$(printf '%s' "$gw_hex" | cut -c5-6)")
        c=$(printf '%d' "0x$(printf '%s' "$gw_hex" | cut -c3-4)")
        d=$(printf '%d' "0x$(printf '%s' "$gw_hex" | cut -c1-2)")
        gw="$a.$b.$c.$d"
      fi
      if [ -n "$gw" ] && reachable "$gw" "$port"; then
        echo "Postgres at ${host}:${port} is not reachable from this container; using host gateway ${gw}:${port}"
        suffix=${url#"jdbc:postgresql://${host}:${port}"}
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${gw}:${port}${suffix}"
      else
        echo "Postgres at ${host}:${port} is not reachable from this container"
      fi
    fi
    ;;
esac

exec java $JAVA_OPTS -jar /app/app.jar
