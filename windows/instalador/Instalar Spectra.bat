@echo off
rem Instalador do Spectra - de dois cliques neste arquivo
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" set "PS=%SystemRoot%\SysWOW64\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" for %%P in (pwsh.exe) do set "PS=%%~$PATH:P"
if "%PS%"=="" (
  echo Nao encontrei o PowerShell neste Windows.
  pause
  exit /b 1
)
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
if errorlevel 1 pause
