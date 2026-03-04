# Minerva — Decentralized P2P Music Sharing Platform

Minerva is a peer-to-peer music sharing desktop application that enables users to share, discover, and stream music directly between peers without a central server. It combines BitTorrent protocol for file distribution with a custom keyword search protocol (MINERVA1) for peer discovery.

## Architecture

```
┌─────────────────────┐       REST API        ┌─────────────────────┐
│   Electron UI       │ ◄──────────────────►  │   Java Backend      │
│   (HTML/CSS/JS)     │   localhost:4567       │   (Javalin REST)    │
└─────────────────────┘                        └────────┬────────────┘
                                                        │
                                        ┌───────────────┼───────────────┐
                                        │               │               │
                                   ┌────▼────┐   ┌──────▼──────┐  ┌────▼────┐
                                   │BitTorrent│   │ MINERVA1    │  │   DHT   │
                                   │ Engine   │   │ TCP Search  │  │ Crawler │
                                   │(bt lib)  │   │ (port 4568) │  │  (Go)   │
                                   └──────────┘   └─────────────┘  └─────────┘
```

### Components

- **Electron Frontend** — Desktop UI built with HTML5, CSS3, and vanilla JavaScript ES modules. Provides library management, album detail views, playlist management, search/discover, and an audio player.
- **Java Backend** — Javalin-based REST API server handling library management, torrent creation/seeding, peer search, metadata extraction, and streaming. Entry point: `com.minerva.MainApp`.
- **BitTorrent Engine** — Based on [atomashpolskiy/bt](https://github.com/atomashpolskiy/bt) v1.10 with patches for Local Service Discovery (LSD) and UPnP port mapping.
- **MINERVA1 Protocol** — Custom TCP protocol (port 4568) enabling keyword-based search across peers. Peers announce themselves via UDP multicast on LAN.
- **DHT Crawler** — Go service using `github.com/anacrolix/dht` to discover peers on the BitTorrent DHT network, exposes an HTTP `/peers` endpoint.

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

## Project Structure

```
├── src/main/java/com/minerva/
│   ├── MainApp.java              # Application entry point
│   ├── StringUtils.java          # Shared utility methods
│   ├── backend/
│   │   └── BackendServer.java    # REST API server (Javalin)
│   ├── model/
│   │   ├── Album.java            # Album data model
│   │   └── MusicFile.java        # Track/music file model
│   ├── network/
│   │   ├── JLibTorrentManager.java   # BitTorrent session management
│   │   ├── TorrentCreator.java       # Torrent file creation
│   │   ├── TorrentMetadata.java      # Torrent metadata model
│   │   ├── TorrentValidator.java     # Torrent structure validation
│   │   └── KeywordSearchServer.java  # MINERVA1 search protocol
│   ├── storage/
│   │   ├── CacheManager.java         # Download cache management
│   │   └── MusicMetadataExtractor.java # Audio metadata extraction
│   ├── playlist/
│   │   └── PlaylistManager.java      # Playlist CRUD (JSON-backed)
│   ├── dht/
│   │   └── DHTManager.java           # DHT integration
│   └── library/
│       └── LibraryManager.java       # Music library management
├── src/test/java/com/minerva/       # JUnit 5 unit tests
├── minerva-electron/                 # Electron frontend
│   ├── js/                           # JavaScript modules
│   ├── css/                          # Stylesheets (BEM components)
│   └── index.html                    # Main UI
├── dht-crawler/                      # Go DHT crawler service
├── docker-compose.yml                # Multi-node Docker setup
└── pom.xml                           # Maven build configuration
```

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

56 unit tests covering:
- `StringUtils` — filename sanitization, hex encoding/decoding
- `PlaylistManager` — CRUD operations, track management, persistence
- `MusicFile` / `Album` — model validation, magnet link generation
- `TorrentMetadata` — serialization, enum behavior, field parsing

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
