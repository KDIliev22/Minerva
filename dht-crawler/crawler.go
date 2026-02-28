package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/anacrolix/dht/v2"
	"github.com/anacrolix/torrent/metainfo"
)

var (
	peerMutex   sync.RWMutex
	peerSet     = make(map[string]bool)
	lastCleanup = time.Now()
	maxPeers    = 50000
)

func main() {
	var httpAddr string
	var dhtPort int
	var bootstrapList string
	var targetInfohash string

	flag.StringVar(&httpAddr, "http", ":8080", "HTTP server address")
	flag.IntVar(&dhtPort, "dht-port", 6881, "DHT UDP port")
	flag.StringVar(&bootstrapList, "bootstrap", "", "Comma-separated bootstrap nodes (host:port)")
	flag.StringVar(&targetInfohash, "infohash", "", "Only store announces with this exact infohash (40-char hex)")
	flag.Parse()

	// Create UDP socket for DHT
	conn, err := net.ListenPacket("udp", fmt.Sprintf("0.0.0.0:%d", dhtPort))
	if err != nil {
		panic(fmt.Sprintf("Failed to listen on UDP port %d: %v", dhtPort, err))
	}
	defer conn.Close()

	// Prepare bootstrap nodes if provided
	var startingNodes dht.StartingNodesGetter
	if bootstrapList != "" {
		// Parse bootstrap list into host:port strings
		var hosts []string
		for _, node := range strings.Split(bootstrapList, ",") {
			node = strings.TrimSpace(node)
			if node != "" {
				hosts = append(hosts, node)
			}
		}
		// Resolve them to dht.Addr
		addrs, err := dht.ResolveHostPorts(hosts)
		if err != nil {
			panic(fmt.Sprintf("Failed to resolve bootstrap nodes: %v", err))
		}
		// StartingNodes is a function that returns these addresses
		startingNodes = func() ([]dht.Addr, error) {
			return addrs, nil
		}
		fmt.Printf("Using bootstrap nodes: %v\n", hosts)
	} else {
		// If no bootstrap list, rely on library's default bootstrap
		startingNodes = nil
	}

	// Create server configuration
	conf := dht.NewDefaultServerConfig()
	conf.Conn = conn
	conf.StartingNodes = startingNodes
	// Set announce callback
	conf.OnAnnouncePeer = func(infoHash metainfo.Hash, ip net.IP, port int, portOk bool) {
		processAnnounce(infoHash, ip, port, targetInfohash)
	}

	s, err := dht.NewServer(conf)
	if err != nil {
		panic(fmt.Sprintf("Failed to start DHT server: %v", err))
	}
	defer s.Close()

	fmt.Printf("DHT server started on port %d\n", dhtPort)

	// Start routing table maintenance
	go s.TableMaintainer()

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

func processAnnounce(infoHash metainfo.Hash, ip net.IP, port int, targetInfohash string) {
	infoHashHex := fmt.Sprintf("%x", infoHash[:])
	if targetInfohash != "" && infoHashHex != targetInfohash {
		return
	}

	ipStr := ip.String()
	if ipStr == "" || port == 0 {
		return
	}

	peerAddr := fmt.Sprintf("%s:%d", ipStr, port)

	peerMutex.Lock()
	defer peerMutex.Unlock()

	if len(peerSet) < maxPeers {
		peerSet[peerAddr] = true
	}

	if time.Since(lastCleanup) > 10*time.Minute {
		lastCleanup = time.Now()
	}
}
