@echo off
title Abrir Poker GTO en Android Studio
set "PROJ_DIR=%~dp0"
if "%PROJ_DIR:~-1%"=="\" set "PROJ_DIR=%PROJ_DIR:~0,-1%"
start "" "C:\Program Files\Android\Android Studio\bin\studio64.exe" "%PROJ_DIR%"
