; Minerva NSIS – Custom Install / Uninstall hooks
; Registers the backend to start silently on Windows login.

!macro customInstall
  ; Create data directories under %APPDATA%\Minerva
  CreateDirectory "$APPDATA\Minerva"
  CreateDirectory "$APPDATA\Minerva\library"
  CreateDirectory "$APPDATA\Minerva\torrent_files"
  CreateDirectory "$APPDATA\Minerva\downloads"
  CreateDirectory "$APPDATA\Minerva\album_art"
  CreateDirectory "$APPDATA\Minerva\uploads"
  CreateDirectory "$APPDATA\Minerva\torrents"

  ; Register backend to run on login (silent VBS launcher)
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" \
    "MinervaBackend" 'wscript.exe "$INSTDIR\resources\backend\start-backend.vbs"'

  ; Start the backend right now
  Exec 'wscript.exe "$INSTDIR\resources\backend\start-backend.vbs"'
!macroend

!macro customUnInstall
  ; Remove startup registry entry
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "MinervaBackend"
!macroend
