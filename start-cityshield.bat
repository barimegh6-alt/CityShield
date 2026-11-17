@echo off
REM CityShield launcher for Windows 11. Starts the backend (port 8080) and frontend (port 5173) in two windows.
REM Prerequisites: JDK 17+, Maven, Node.js 18+, MySQL 8 with database "cityshield" (see SETUP_WINDOWS.md).
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist ".env" (
  echo [ERROR] .env not found. Copy .env.example to .env and set CITYSHIELD_DB_PASSWORD first.
  pause
  exit /b 1
)

REM Load KEY=VALUE lines from .env into this process only (values are not echoed).
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
  if not "%%A"=="" set "%%A=%%B"
)

if "%CITYSHIELD_DB_PASSWORD%"=="" (
  echo [ERROR] CITYSHIELD_DB_PASSWORD is empty in .env
  pause
  exit /b 1
)
if "%CITYSHIELD_DB_PASSWORD:~0,1%"=="<" (
  echo [ERROR] CITYSHIELD_DB_PASSWORD still contains the placeholder. Edit .env.
  pause
  exit /b 1
)

where mvn >nul 2>nul || (echo [ERROR] Maven ^(mvn^) not found on PATH & pause & exit /b 1)
where npm >nul 2>nul || (echo [ERROR] Node.js ^(npm^) not found on PATH & pause & exit /b 1)

if not exist "frontend\node_modules" (
  echo Installing frontend dependencies...
  pushd frontend
  call npm install
  popd
)

echo Starting backend on http://localhost:8080 ...
start "CityShield backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"

echo Starting frontend on http://localhost:5173 ...
start "CityShield frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo Wait for "Started CityShieldApplication" in the backend window, then open http://localhost:5173
echo Health check: http://localhost:8080/api/v1/health
endlocal
