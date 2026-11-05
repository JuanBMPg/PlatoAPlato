@echo off
chcp 65001 >nul
title Plato a Plato - Instalar Android Studio
set "PROYECTO=%~dp0"
set "STUDIO=%ProgramFiles%\Android\Android Studio\bin\studio64.exe"

if exist "%STUDIO%" goto abrir

echo ============================================================
echo  Instalando Android Studio (unos 1,2 GB). Puede tardar
echo  varios minutos. Acepta el aviso de administrador de Windows.
echo ============================================================
where winget >nul 2>nul
if errorlevel 1 (
  echo No se encuentra winget en este PC.
  echo Descarga Android Studio desde https://developer.android.com/studio
  pause
  exit /b 1
)
winget install -e --id Google.AndroidStudio --accept-package-agreements --accept-source-agreements
if not exist "%STUDIO%" (
  echo.
  echo La instalacion no se ha completado. Revisa los mensajes de arriba.
  pause
  exit /b 1
)

:abrir
echo Abriendo el proyecto en Android Studio...
start "" "%STUDIO%" "%PROYECTO:~0,-1%"
timeout /t 5 >nul
exit /b 0
