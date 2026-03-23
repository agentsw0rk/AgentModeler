#!/bin/bash

PROMPT="${1:-Hallo!}"

curl -s -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d "{\"prompt\": \"$PROMPT\"}" | jq .
