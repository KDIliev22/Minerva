<p align="center">
  <img src="minerva-electron/assets/minervalogo.png" alt="Minerva" width="200"/>
</p>

# Minerva

Decentralized P2P music sharing platform. Share, discover and stream music directly between peers — no central server needed.

Built with BitTorrent for file transfer and a custom MINERVA1 protocol for keyword search across the network.

## Architecture

```
  Electron UI  <--- REST (localhost:4567) --->  Java Backend
  (HTML/CSS/JS)                                     |
                                          +---------+---------+
                                          |         |         |
                                       BitTorrent  MINERVA1  DHT Crawler
                                       (bt lib)    (TCP:4568)   (Go)
```

- **Electron Frontend** — HTML5/CSS3/JS desktop UI for library, playlists, search, and playback
- **Java Backend** — Javalin REST API handling uploads, streaming, torrent management and peer search
- **BitTorrent Engine** — [atomashpolskiy/bt](https://github.com/atomashpolskiy/bt) v1.10 with LSD and UPnP patches
- **MINERVA1 Protocol** — custom TCP protocol for keyword search between peers (UDP multicast on LAN)
- **DHT Crawler** — Go service using `anacrolix/dht` to find peers, exposes HTTP `/peers` endpoint

## Tech Stack

| Layer       | Technology                                      |
|-------------|------------------------------------------------|
| Frontend    | Electron 28, HTML5, CSS3, JavaScript ES Modules |
| Backend     | Java 17, Javalin 4.6, Maven                     |
| P2P         | bt (BitTorrent), custom MINERVA1 TCP protocol   |
| DHT         | Go, anacrolix/dht v2.23                          |
| Audio       | JLayer (MP3), jaudiotagger (metadata)            |
| Storage     | SQLite (via JDBC), JSON files                    |
| Port Mapping| jUPnP 3.0.2 (UPnP/NAT-PMP)                      |
| Containers  | Docker, docker-compose (multi-node testing)      |

## Prerequisites

- **Java 17** (JDK)
- **Maven 3.8+**
- **Node.js 18+** and **npm** (for Electron frontend)
- **Go 1.21+** (for DHT crawler, optional)
- **Docker** (for multi-node testing, optional)

## Building

### Backend (Java)

```bash
mvn clean package -DskipTests
```

The fat JAR is produced at `target/minerva-1.0-SNAPSHOT.jar`.

### Frontend (Electron)

```bash
cd minerva-electron
npm install
npm start
```

### DHT Crawler (Go)

```bash
cd dht-crawler
go mod tidy
go build -o dht-crawler
```

## Running

### Single Node

```bash
# Start the Java backend
./start_seed.sh

# In another terminal, start the Electron UI
cd minerva-electron && npm start
```

### Multi-Node (Docker)

```bash
docker-compose up --build
```

This creates two interconnected nodes on a custom bridge network for testing P2P functionality.

## Running Tests

```bash
mvn test
```

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
