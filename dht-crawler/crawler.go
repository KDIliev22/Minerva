package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/shiyanhui/dht"
)

var (
	peerMutex   sync.RWMutex
	peerSet     = make(map[string]bool)
	lastCleanup = time.Now()
	maxPeers    = 50000
)

func main() {
	var httpAddr string
	flag.StringVar(&httpAddr, "http", ":8080", "HTTP server address (e.g., :8080)")
	flag.Parse()

	// Create crawler config (DHT port is hardcoded to 6881 by the library)
	config := dht.NewCrawlConfig()
	config.MaxNodes = 20000
	config.BlackListMaxSize = 5000

	fmt.Println("DHT crawler will listen on default port 6881")
	fmt.Println("Make sure this port is free (stop any other DHT clients)")

	// Callback for announce_peer messages
	config.OnAnnouncePeer = func(infoHash, ip string, port int) {
		if ip == "" || port <= 0 || port > 65535 {
			return
		}
		peerAddr := fmt.Sprintf("%s:%d", ip, port)

		peerMutex.Lock()
		defer peerMutex.Unlock()

		if len(peerSet) < maxPeers {
			peerSet[peerAddr] = true
		}

		if time.Since(lastCleanup) > 10*time.Minute {
			lastCleanup = time.Now()
		}
	}

	// Create and start the DHT crawler
	d := dht.New(config)
	go d.Run()
	fmt.Println("DHT crawler started")

	// HTTP endpoint for Java to fetch discovered peers
	http.HandleFunc("/peers", func(w http.ResponseWriter, r *http.Request) {
		peerMutex.RLock()
		defer peerMutex.RUnlock()

		peers := make([]string, 0, len(peerSet))
		for p := range peerSet {
			peers = append(peers, p)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(peers)
	})

	fmt.Printf("HTTP server listening on %s\n", httpAddr)
	if err := http.ListenAndServe(httpAddr, nil); err != nil {
		panic(err)
	}
}
