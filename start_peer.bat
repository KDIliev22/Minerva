@echo off
setlocal

set CRAWLER_DIR=.\dht-crawler
set CRAWLER_BIN=%CRAWLER_DIR%\crawler.exe
set CRAWLER_URL=http://localhost:8080/peers
set INFOHASH=2a242fcb7604ddcf795af2c60546a1b7e2be3f40
set BOOTSTRAP=87.120.14.80:6882

if not exist "%CRAWLER_BIN%" (
    echo Crawler binary not found. Please build it first:
    echo   cd %CRAWLER_DIR% && go build -o crawler.exe crawler.go
    exit /b 1
)

echo Starting DHT crawler (peer) with bootstrap %BOOTSTRAP%...
start /min "Minerva Crawler" "%CRAWLER_BIN%" -http :8080 -dht-port 6882 -bootstrap "%BOOTSTRAP%" -infohash %INFOHASH%

timeout /t 2 /nobreak >nul

set CRAWLER_URL=%CRAWLER_URL%
set LISTEN_PORT=6882

echo Starting Minerva backend on DHT port %LISTEN_PORT%...
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.lang.invoke=ALL-UNNAMED ^
     -cp target/minerva-1.0.0.jar com.minerva.MainApp

echo.
echo Minerva backend stopped. Please close the crawler window manually.
pause
