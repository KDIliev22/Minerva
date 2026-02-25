' Minerva Backend – Silent Launcher (Windows)
' Starts the Java backend without showing a console window.
' Called on login via the Windows Registry Run key.

Set objShell = CreateObject("WScript.Shell")
Set objFSO   = CreateObject("Scripting.FileSystemObject")

' ── Paths ──
strBackendDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strDataDir    = objShell.ExpandEnvironmentStrings("%APPDATA%") & "\Minerva"
strJava       = strBackendDir & "\jre\bin\javaw.exe"
strJar        = strBackendDir & "\minerva-backend.jar"

' ── Verify files exist ──
If Not objFSO.FileExists(strJava) Then
    WScript.Quit 1
End If
If Not objFSO.FileExists(strJar) Then
    WScript.Quit 1
End If

' ── Create data directories ──
If Not objFSO.FolderExists(strDataDir) Then objFSO.CreateFolder(strDataDir)
For Each subDir In Array("library", "torrent_files", "downloads", "album_art", "uploads", "torrents")
    strSub = strDataDir & "\" & subDir
    If Not objFSO.FolderExists(strSub) Then objFSO.CreateFolder(strSub)
Next

' ── Set environment variables for this process ──
Set objEnv = objShell.Environment("Process")
objEnv("API_PORT")      = "4567"
objEnv("SEARCH_PORT")   = "4568"
objEnv("LISTEN_PORT")   = "6881"
objEnv("LIBRARY_DIR")   = strDataDir & "\library"
objEnv("TORRENT_DIR")   = strDataDir & "\torrent_files"
objEnv("DOWNLOADS_DIR") = strDataDir & "\downloads"
objEnv("ALBUM_ART_DIR") = strDataDir & "\album_art"

' ── Launch backend silently (0 = hidden, False = don't wait) ──
objShell.CurrentDirectory = strDataDir
objShell.Run """" & strJava & """ -jar """ & strJar & """", 0, False

Set objShell = Nothing
Set objFSO   = Nothing
