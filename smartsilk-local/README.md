# SmartSilk - Complete Local Distribution

Run SmartSilk 100% locally on your computer with complete data privacy, zero cloud costs, and no external dependencies.

---

### Quick Start (3 Steps)

#### 1. Start the Local Engine
- **On Windows:** Double-click `start.bat`
- **On Linux / Mac:** Open terminal in this folder and run:
  ```bash
  ./start.sh
  ```
The database and backend engine will automatically start in the background and bind safely to dedicated isolated port `http://localhost:8088` (preventing any conflicts with developer services).

#### 2. Open SmartSilk Desktop App
- Install the SmartSilk app using `smartsilk_1.0.0_amd64.deb` (Linux) or `SmartSilk.msi` (Windows).
- Double-click the **SmartSilk** desktop icon.
- It will automatically connect to `http://localhost:8088`.

#### 3. First-Run Setup (Takes 30 seconds)
- The **First-Run Setup Wizard** will appear.
- Enter your **Business Name** (e.g. *Sri Lakshmi Silks*), create your **Username** & **Password**, and click **Initialize & Launch**.
- You are in! You can now manage your purchases, customer orders, silk inventory, worker wages, and payments.

---

### How to Stop
- **On Windows:** Double-click `stop.bat`
- **On Linux / Mac:** Run `./stop.sh`

---

### Where is My Data Stored?
All your data lives inside this folder on your computer:
- **Database & Ledgers:** `smartsilk-local/data/postgres/`
- **Attached Bills & Documents:** `smartsilk-local/data/documents/`

To back up your entire business, you can simply make a copy of this folder or use the **Backup & Safety** button inside SmartSilk Settings!

---

### How to Reset to a Fresh, Clean Database
If you ever want to completely erase test data and start over with a brand new clean ledger:
- **On Windows:** Double-click `reset-database.bat` and type `YES` when prompted.
- **On Linux / Mac:** Run `./reset-database.sh` and type `YES` when prompted.
- Then run `start.bat` / `./start.sh` to launch a completely empty database ready for first-run setup.

---

### Password Recovery
- If you forget your master password for the local engine, you can reset to a fresh database using `reset-database.bat`.
- For staff accounts, the business owner can reset passwords anytime in **Settings > User Management**.

