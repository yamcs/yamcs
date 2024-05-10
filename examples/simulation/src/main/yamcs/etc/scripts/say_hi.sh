#!/bin/sh

echo "Creating event"

MSG="$(whoami) says hi"
JSON_STRING=$(printf '{"message": "%s"}' "$MSG")

curl -XPOST $YAMCS_URL/api/archive/$YAMCS_INSTANCE/events --silent -d "$JSON_STRING" --fail-with-body
