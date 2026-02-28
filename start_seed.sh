#!/bin/bash

CRAWLER_DIR="./dht-crawler"
CRAWLER_BIN="$CRAWLER_DIR/crawler"
CRAWLER_URL="http://localhost:8080/peers"
INFOHASH="2a242fcb7604ddcf795af2c60546a1b7e2be3f40"

if [ ! -f "$CRAWLER_BIN" ]; then
    echo "Crawler binary not found. Please build it first:"
    echo "  cd $CRAWLER_DIR && go build -o crawler crawler.go"
    exit 1
fi

# Start crawler as seed (no bootstrap)
echo "Starting DHT crawler (seed) with infohash $INFOHASH..."
"$CRAWLER_BIN" -http :8080 -dht-port 6882 -infohash "$INFOHASH" > crawler.log 2>&1 &
CRAWLER_PID=$!

sleep 2
if ! kill -0 $CRAWLER_PID 2>/dev/null; then
    echo "Crawler failed to start. Check crawler.log"
    exit 1
fi
echo "Crawler started with PID $CRAWLER_PID"

export CRAWLER_URL="$CRAWLER_URL"
export LISTEN_PORT="6882"

echo "Starting Minerva backend on DHT port $LISTEN_PORT..."
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
     -cp target/minerva-1.0.0.jar com.minerva.MainApp

echo "Stopping crawler..."
kill $CRAWLER_PID
