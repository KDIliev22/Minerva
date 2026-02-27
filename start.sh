#!/bin/bash

# Paths
CRAWLER_DIR="./dht-crawler"
CRAWLER_BIN="$CRAWLER_DIR/crawler"
CRAWLER_URL="http://localhost:8080/peers"
CIDR_FILE="$CRAWLER_DIR/bg_cidrs.txt"   # <-- your CIDR list

# Check if crawler binary exists
if [ ! -f "$CRAWLER_BIN" ]; then
    echo "Crawler binary not found. Please build it first:"
    echo "  cd $CRAWLER_DIR && go build -o crawler crawler.go"
    exit 1
fi

# Check if CIDR file exists
if [ ! -f "$CIDR_FILE" ]; then
    echo "CIDR file not found: $CIDR_FILE"
    exit 1
fi

# Start the crawler in the background
echo "Starting DHT crawler with CIDR filter..."
"$CRAWLER_BIN" -http :8080 -cidr "$CIDR_FILE" > crawler.log 2>&1 &
CRAWLER_PID=$!

# Give it a moment to start
sleep 2

# Check if crawler is still running
if ! kill -0 $CRAWLER_PID 2>/dev/null; then
    echo "Crawler failed to start. Check crawler.log"
    exit 1
fi

echo "Crawler started with PID $CRAWLER_PID"

# Set environment variables for Java
export CRAWLER_URL="$CRAWLER_URL"
export LISTEN_PORT="6882"   # Avoid port conflict with crawler

echo "Starting Minerva backend on DHT port $LISTEN_PORT..."
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
     -cp target/minerva-1.0.0.jar com.minerva.MainApp

# When Java exits, kill the crawler
echo "Stopping crawler..."
kill $CRAWLER_PID
