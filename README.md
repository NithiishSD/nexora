# SmartSilk (Nexora)

Business system for a family silk-yarn trading and warping business: purchases and receipts, stock and work in
progress, orders and deliveries, production and outsourcing, worker work, payments with allocation, expenses, loans,
chits, accounts, reports and documents. Stock, receivables, payables and cash are always derived from two
append-only ledgers, so every figure can be traced to the entries behind it.

| Part | Folder | Stack |
|---|---|---|
| Server | `Nexora-backend/` | Java 21, Spring Boot 4, PostgreSQL 17, Flyway |
| Desktop app | `nexora-desktop/` | Kotlin, Compose Desktop (Windows and Linux) |
| Android app | `smartledger-android/` | Android, Jetpack Compose, Kotlin |
| Standalone local runner | `smartsilk-local/` | Docker Compose, Shell / Batch 1-click scripts |
| Production deployment | `deploy/` | Docker Compose, Caddy (HTTPS), scripts for deploy, backup, restore and monitoring |

---

## Download & Run (For End Users — No Source Code Required)

If you are a business user or client, **you do NOT need to clone this repository, install development tools, or compile code.** You can download, install, and run the pre-built application directly.

### 1. Download the Windows Release Package
Get the compiled distribution package `SmartSilk-Windows-v1.0.0.zip` (available under GitHub Releases / Artifacts):
- **`1-Install-SmartSilk.msi`** — Complete desktop application installer with bundled runtime (no Java installation required).
- **`Local-Engine/`** — 1-click local database and backend engine (for running 100% offline without an external cloud server).
- **`Readme-Instructions.txt`** — Quick-start instructions.

### 2. Install SmartSilk on Windows
1. Double-click **`1-Install-SmartSilk.msi`**.
2. Follow the standard Windows setup wizard (Next $\rightarrow$ Install).
3. A **SmartSilk** shortcut will be added to your **Desktop** and **Start Menu**.

### 3. Launch & Connect: Choose Your Mode

When opening SmartSilk, the connection screen offers two options:

* **Option 1: Run Locally on This PC (Offline Standalone)**
  - Ensure [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) is running.
  - In the `Local-Engine` folder, double-click **`start.bat`** *(or click "Use Local Engine" inside the app to auto-launch it)*.
  - In SmartSilk, click **"Use Local Engine (localhost)"**.
  - All your company data is stored safely and privately on your computer in `Local-Engine/data/`.
  - To stop when finished, double-click **`stop.bat`**.

* **Option 2: Connect to Remote / Cloud Server**
  - Simply open the SmartSilk desktop app.
  - Enter your central shop server or cloud URL (e.g. `https://shop.example.com`).
  - Click **"Test Connection"** and log in with your credentials. *(No local engine or Docker required).*

> **Switch Anytime:** Inside the SmartSilk app, navigate to **Settings $\rightarrow$ Server & Connection Mode** to seamlessly switch between **Local Engine** and a **Remote Server**.

---

## Setup

*(For developers contributing to or building the system from source)*

Needs: JDK 21, Docker with the compose plugin (the tests start PostgreSQL in containers), Git. The Gradle wrapper
downloads everything else.

```bash
git clone <repository url> && cd <repository>/Nexora-backend
cp .env.example .env                 # local development values only; .env is never committed
docker compose up -d                 # PostgreSQL 17 on localhost:5431
./gradlew dependencies -q > /dev/null
```

Every setting is an environment variable with a comment in `Nexora-backend/.env.example` (development) and
`deploy/.env.prod.example` (production). The development profile is the default and has safe local values, so the
server starts without setting anything.

## Run

Server (from `Nexora-backend/`):

```bash
./gradlew bootRun
```

It migrates the database, creates the first owner account (`owner` / `DevOwner#2026pass` in development) and listens
on `http://localhost:8080`. API documentation: `http://localhost:8080/swagger-ui.html`. Health:
`curl http://localhost:8080/actuator/health`.

Desktop app (from `nexora-desktop/`):

```bash
./gradlew run
```

On first start it asks for the server address: `http://localhost:8080` for development (shown with a warning), the
`https://` address of the server otherwise.

## Test

From `Nexora-backend/` (Docker must be running):

| What | Command |
|---|---|
| Compile with every warning as an error | `./gradlew compileJava compileTestJava` |
| All unit and integration tests | `./gradlew test` |
| End-to-end API scenarios | `./gradlew test --tests '*E2E*'` |
| Coverage report and 80 % gate | `./gradlew jacocoTestReport jacocoTestCoverageVerification` |
| Everything, as CI runs it | `./gradlew build` |
| Performance suite (slow, separate) | `./gradlew test -Pperformance` |
| Dependency audit (needs `NVD_API_KEY`) | `./gradlew dependencyCheckAnalyze` |

From `nexora-desktop/`: `./gradlew check` (lint, type checks and tests).

From `smartledger-android/`: `./gradlew test` (unit and viewmodel tests).

## Deploy

Production runs on one Linux server with Docker: the server image, PostgreSQL and Caddy for HTTPS
(`deploy/docker-compose.prod.yml`).

1. Every push to `main` runs CI (`.github/workflows/ci.yml`): build and tests, dependency audit, secret scan,
   compose check, then the image is scanned, smoke-tested and published to GitHub Container Registry.
2. A release is deployed with the **Deploy** workflow (`.github/workflows/deploy.yml`): enter the image tag, the
   owner approves, and `deploy/scripts/deploy.sh` on the server takes a backup, starts the new version, checks it and
   rolls back by itself if the check fails.
3. Desktop installers: `./gradlew packageDistributionForCurrentOS` in `nexora-desktop/` (MSI on Windows, DEB on
   Linux).

The go-live data import is in [`deploy/import/README.md`](deploy/import/README.md).
