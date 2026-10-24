# SmartSilk (Nexora)

Business system for a family silk-yarn trading and warping business: purchases and receipts, stock and work in
progress, orders and deliveries, production and outsourcing, worker work, payments with allocation, expenses, loans,
chits, accounts, reports and documents. Stock, receivables, payables and cash are always derived from two
append-only ledgers, so every figure can be traced to the entries behind it.

| Part | Folder | Stack |
|---|---|---|
| Server | `Nexora-backend/` | Java 21, Spring Boot 4, PostgreSQL 17, Flyway |
| Desktop app | `nexora-desktop/` | Kotlin, Compose Desktop (Windows and Linux) |
| Production deployment | `deploy/` | Docker Compose, Caddy (HTTPS), scripts for deploy, backup, restore and monitoring |
| Documentation | `docs/` | requirements, architecture, data model, API, testing, security, deployment, runbook |

---

## Download, Install & Uninstall (For End Users — No Source Code Needed)

If you are an end-user, business manager, or tester, **you do not need to clone the source code, install Java, or build the project.** Pre-built standalone application packages are automatically built by CI and available under **GitHub Actions $\rightarrow$ Desktop $\rightarrow$ Artifacts**.

### 1. Download Options
| Platform | Artifact Name | Download Contents | Description |
|:---|:---|:---|:---|
| **Windows Installer** | `smartsilk-windows-installer` | `SmartSilk-1.0.0.msi` | Standard Windows setup wizard (installs to Program Files) |
| **Windows Portable** | `smartsilk-windows-portable` | `SmartSilk-Windows-Portable.zip` | Standalone portable application (extract and run directly) |
| **Linux Package** | `smartsilk-linux-deb` | `smartsilk_1.0.0_amd64.deb` | Native Debian/Ubuntu package (`sudo dpkg -i ...`) |

---

### 2. Windows Installation & Uninstallation

#### Installation:
- **Standard Installer (`.msi`):** Double-click `SmartSilk-1.0.0.msi` and follow the setup wizard. It installs into `Program Files` and creates a Desktop shortcut.
- **Portable Zip (`.zip`):** Extract `SmartSilk-Windows-Portable.zip` to any folder and double-click `SmartSilk.exe`. No installation required!

#### Uninstallation:
1. Open Windows **Settings** $\rightarrow$ **Apps** $\rightarrow$ **Installed Apps** (or Control Panel $\rightarrow$ Programs and Features).
2. Locate **SmartSilk** in the list.
3. Click the menu (`...`) and select **Uninstall**.

---

### 3. Linux Installation & Uninstallation

#### Installation:
Download `smartsilk_1.0.0_amd64.deb` and install via terminal:
```bash
sudo dpkg -i smartsilk_1.0.0_amd64.deb
# If any dependencies are needed:
sudo apt-get install -f
```
The **SmartSilk** application icon will immediately appear in your desktop application launcher / Super Menu.

#### Uninstallation:
To cleanly remove the application from your Linux computer:
```bash
sudo apt remove smartsilk
```
To remove the application and purge any system configurations:
```bash
sudo apt purge smartsilk
```
To remove user cache and saved settings:
```bash
rm -rf ~/.config/smartsilk ~/.local/share/smartsilk
```

#### Why does Linux Settings display "Not sandboxed"?
- Linux desktop environments (such as GNOME / Ubuntu Settings $\rightarrow$ Applications) classify containerized applications (like Flatpak or Snap) as "Sandboxed".
- Native Debian packages (`.deb`)—such as Google Chrome, VS Code, Steam, and SmartSilk—install directly to native system paths (`/opt/smartsilk`) with direct access to native Wayland/X11 rendering, hardware acceleration, and system file dialogs.
- "Not sandboxed" is standard for native Linux software and confirms that the app runs natively with zero container virtualization overhead.

---

## Setup (For Developers)

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

From `nexora-desktop/`: `./gradlew check` (lint, type checks and tests). Each requirement in
`docs/02-REQUIREMENTS.md` is proven by a test whose name contains its ID (`docs/08-TESTING.md`).

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

The first server setup, rollback, restore, secret rotation, user accounts and alerts are step by step in
[`docs/RUNBOOK.md`](docs/RUNBOOK.md); the go-live data import is in [`deploy/import/README.md`](deploy/import/README.md);
the reasons behind the deployment design are in [`docs/10-DEPLOYMENT.md`](docs/10-DEPLOYMENT.md).
