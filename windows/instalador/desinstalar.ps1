# Remove o Spectra do Windows (mantém ou apaga as configurações, conforme a escolha)
$dest = Join-Path $env:LOCALAPPDATA 'Spectra'
$app = Join-Path $dest 'app'
$data = Join-Path $env:APPDATA 'Spectra'
Write-Host ''
Write-Host '  Desinstalar o Spectra' -ForegroundColor Cyan
$py = Get-ChildItem "$env:LOCALAPPDATA\Programs\Python\Python3*\pythonw.exe", "$env:ProgramFiles\Python3*\pythonw.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($py -and (Test-Path $app)) { try { Push-Location $app; & $py.FullName -m spectra --parar 2>$null | Out-Null } catch {} finally { Pop-Location } }
Start-Sleep -Seconds 1
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Spectra.lnk')
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath('Startup')) 'Spectra.lnk')
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path ([Environment]::GetFolderPath('Programs')) 'Spectra')
$ans = Read-Host '  Apagar também as configurações (seções, wallpapers, atalhos)? (s/N)'
if ($ans -match '^[sS]') { Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $data }
Set-Location $env:TEMP
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $dest
Write-Host '  Spectra removido. Seus apps do Windows não foram alterados.' -ForegroundColor Green
Start-Sleep -Seconds 4
