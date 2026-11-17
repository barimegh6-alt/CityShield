# CityShield — Windows 11 setup

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Apache Maven | 3.9+ | `mvn -version` |
| Node.js | 18 or newer (includes npm) | `node -v` and `npm -v` |
| MySQL Server | 8.x | `mysql --version` |

If a check fails, install the tool and open a **new** terminal.

## 2. Database

Using your MySQL admin account (you choose and keep the passwords yourself):

```sql
CREATE DATABASE IF NOT EXISTS cityshield CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'cityshield_app'@'localhost' IDENTIFIED BY '<choose-a-password>';
GRANT ALL PRIVILEGES ON cityshield.* TO 'cityshield_app'@'localhost';
FLUSH PRIVILEGES;
```

The application creates the tables itself (`CREATE TABLE IF NOT EXISTS`) and loads the synthetic data **only if `city_zones` is empty**. It never drops or overwrites existing data.

## 3. Configuration

```
copy .env.example .env
notepad .env
```

Replace `<your-mysql-password-here>` with the password you chose. Never commit or share `.env`.

## 4. Run

Easiest: double-click **`start-cityshield.bat`** (it reads `.env`, installs frontend packages if needed, and opens two windows).

Manual alternative (PowerShell):

```powershell
# window 1 - backend
cd backend
$env:CITYSHIELD_DB_PASSWORD = Read-Host "DB password"     # typed at runtime, not stored
mvn spring-boot:run

# window 2 - frontend
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Health check: http://localhost:8080/api/v1/health. DB self-check: http://localhost:8080/api/v1/admin/db-check.

## 5. Build and test

```powershell
cd backend ; mvn clean test ; mvn clean package
cd ..\frontend ; npm install ; npm run build
```

`mvn test` runs the algorithm unit tests (no database) and the API integration tests (in-memory H2, no MySQL needed).

## 6. Benchmarks

The Performance page runs the benchmark harness on your machine and stores the rows in MySQL. To produce a CSV from the command line:

```powershell
cd backend
mvn -q compile
java -cp target\classes com.cityshield.benchmark.BenchmarkHarness ..\data\benchmarks\benchmark_results.csv 7
```

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `Access denied for user 'cityshield_app'` | Password in `.env` does not match the MySQL user. |
| `Communications link failure` | MySQL service not running (`services.msc` → MySQL80). |
| Port 8080 or 5173 busy | Close the other program or change `server.port` / the Vite port. |
| Blank map tiles | No internet: the map shows data only; everything else still works. |
| Frontend says "Cannot reach the backend" | Start the backend first and wait for "Started CityShieldApplication". |
