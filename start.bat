@echo off
:: Start Minerva

set LISTEN_PORT=6882

echo Starting Minerva on bt port %LISTEN_PORT%...
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.lang.invoke=ALL-UNNAMED ^
     -cp target/minerva-1.0.0.jar com.minerva.MainApp

pause
