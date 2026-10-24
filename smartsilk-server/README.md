# SmartSilk Server Engine (Standalone Distribution)

The dedicated backend engine and database server package for SmartSilk. Use this package to host the central SmartSilk server on a shop computer, office server, or cloud VPS so multiple desktop and mobile clients can connect concurrently.

---

### Prerequisites
- [Docker](https://docs.docker.com/get-docker/) (or Docker Desktop on Windows) installed.

---

### Quick Start

#### On Linux / macOS
```bash
chmod +x start-server.sh stop-server.sh
./start-server.sh
```

#### On Windows
Double-click `start-server.bat`.

---

### How Desktop Clients Connect
1. The server starts by default on port `8088`.
2. On any computer with the SmartSilk Desktop App:
   - Launch **SmartSilk**.
   - Select **Remote / Cloud Server**.
   - Enter your server address:
     - Local network: `http://<SERVER-LOCAL-IP>:8088` (or `https://...` if reverse proxy is used)
     - Cloud / Domain: `https://your-domain.com`
   - Click **Test connection** $\rightarrow$ **Continue**.

---

### Stopping the Server
- **Linux / macOS:** `./stop-server.sh`
- **Windows:** Double-click `stop-server.bat`

---

### Persistent Data Storage
All data is stored safely in Docker volumes:
- **Ledger Database:** `smartsilk_server_postgres_data`
- **Uploaded Documents & Invoices:** `smartsilk_server_documents_data`
