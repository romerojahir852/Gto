@echo off
chcp 65001 > nul
title Poker GTO Vision - Ejecutor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
