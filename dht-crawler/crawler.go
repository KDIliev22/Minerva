package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/shiyanhui/dht"
)

var (
	peerMutex     sync.RWMutex
	peerSet       = make(map[string]bool) // only BG peers
	lastCleanup   = time.Now()
	maxPeers      = 50000
	bulgariaCIDRs []*net.IPNet // loaded from file
)

func main() {
	var httpAddr string
	var cidrFile string
	flag.StringVar(&httpAddr, "http", ":8080", "HTTP server address")
	flag.StringVar(&cidrFile, "cidr", "bg_cidrs.txt", "File with one CIDR per line (Bulgarian ranges)")
	flag.Parse()

	// 1. Load Bulgarian CIDR ranges
	if err := loadCIDRs(cidrFile); err != nil {
		panic(fmt.Sprintf("Failed to load CIDR file: %v", err))
	}
	fmt.Printf("✅ Loaded %d Bulgarian CIDR ranges\n", len(bulgariaCIDRs))

	// 2. Create crawler config
	config := dht.NewCrawlConfig()
	config.MaxNodes = 20000
	config.BlackListMaxSize = 5000

	// 3. Callback – only accept peers from Bulgaria
	config.OnAnnouncePeer = func(infoHash, ip string, port int) {
		if ip == "" || port <= 0 || port > 65535 {
			return
		}

		// Check if IP is Bulgarian
		if !isBulgarianIP(ip) {
			return // ignore non-BG peers
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

	// 4. Start DHT crawler
	d := dht.New(config)
	go d.Run()
	fmt.Println("🚀 DHT crawler started – only storing BG peers")

	// 5. HTTP endpoint for Java
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

	fmt.Printf("🌍 HTTP server listening on %s\n", httpAddr)
	if err := http.ListenAndServe(httpAddr, nil); err != nil {
		panic(err)
	}
}

// loadCIDRs reads a file with one CIDR per line and stores them in bulgariaCIDRs
func loadCIDRs(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		// Skip empty lines and comments
		if line == "" || line[0] == '#' {
			continue
		}
		_, ipnet, err := net.ParseCIDR(line)
		if err != nil {
			fmt.Printf("⚠️  Skipping invalid CIDR: %s\n", line)
			continue
		}
		bulgariaCIDRs = append(bulgariaCIDRs, ipnet)
	}
	return scanner.Err()
}

// isBulgarianIP checks if an IP belongs to any of the loaded Bulgarian CIDR ranges
func isBulgarianIP(ipStr string) bool {
	ip := net.ParseIP(ipStr)
	if ip == nil {
		return false
	}
	for _, cidr := range bulgariaCIDRs {
		if cidr.Contains(ip) {
			return true
		}
	}
	return false
}
