@echo off
setlocal

REM Paths
set CRAWLER_DIR=.\dht-crawler
set CRAWLER_BIN=%CRAWLER_DIR%\crawler.exe
set CRAWLER_URL=http://localhost:8080/peers

REM Check if crawler exists
if not exist "%CRAWLER_BIN%" (
    echo Crawler binary not found. Please build it first:
    echo   cd %CRAWLER_DIR% && go build -o crawler.exe crawler.go
    exit /b 1
)

REM Start the crawler in a new window (minimized)
echo Starting DHT crawler...
start /min "Minerva Crawler" "%CRAWLER_BIN%" -listen :8080

REM Wait a bit for crawler to start
timeout /t 2 /nobreak >nul

REM Set environment variable and run Java app
set CRAWLER_URL=%CRAWLER_URL%

echo Starting Minerva backend...
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.lang.invoke=ALL-UNNAMED ^
     -cp target/minerva-1.0.0.jar com.minerva.MainApp

REM Java exited, prompt user to close crawler manually
echo.
echo Minerva backend stopped. Please close the crawler window manually.
pause