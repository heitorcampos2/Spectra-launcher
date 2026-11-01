# Instalador do Spectra para Windows
# Copia o programa para %LOCALAPPDATA%\Spectra, instala o Python se faltar
# e cria os atalhos na Área de Trabalho e no Menu Iniciar.
$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'Instalando o Spectra'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = Join-Path $env:LOCALAPPDATA 'Spectra'
$app = Join-Path $dest 'app'

function Say($t, $c = 'White') { Write-Host $t -ForegroundColor $c }

function Test-Py($exe) {
    if (-not $exe -or -not (Test-Path $exe)) { return $false }
    if ($exe -like '*WindowsApps*') { return $false }   # atalho da Microsoft Store, não é o Python de verdade
    try {
        $ok = & $exe -c "import sys; print(sys.version_info >= (3, 9))" 2>$null
        return ($ok -eq 'True')
    } catch { return $false }
}

function Find-Python {
    $cands = @()
    # o PATH deste Windows pode estar incompleto: procura também pelos caminhos padrão
    $launchers = @('py', 'python', "$env:SystemRoot\py.exe", "$env:LOCALAPPDATA\Programs\Python\Launcher\py.exe")
    foreach ($cmd in $launchers) {
        try {
            $pyArgs = if ($cmd -like '*py' -or $cmd -like '*py.exe') { @('-3', '-c', 'import sys; print(sys.executable)') } else { @('-c', 'import sys; print(sys.executable)') }
            $p = & $cmd @pyArgs 2>$null
            if ($LASTEXITCODE -eq 0 -and $p) { $cands += $p.Trim() }
        } catch {}
    }
    $cands += Get-ChildItem -Path "$env:LOCALAPPDATA\Programs\Python\Python3*\python.exe", "$env:ProgramFiles\Python3*\python.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | ForEach-Object { $_.FullName }
    foreach ($c in $cands) { if (Test-Py $c) { return $c } }
    return $null
}

Say ''
Say '  Spectra para Windows - instalação' 'Cyan'
Say '  ---------------------------------' 'Cyan'
Say ''

# 1) Python
$py = Find-Python
if (-not $py) {
    Say '  O Spectra precisa do Python. Instalando o Python 3.12 (só para este usuário)...' 'Yellow'
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget -and (Test-Path "$env:LOCALAPPDATA\Microsoft\WindowsApps\winget.exe")) { $winget = "$env:LOCALAPPDATA\Microsoft\WindowsApps\winget.exe" }
    if (-not $winget) {
        Say '  Não encontrei o winget para instalar o Python automaticamente.' 'Red'
        Say '  Abrindo a página do Python: instale, marque "Add python.exe to PATH" e rode este instalador de novo.' 'Red'
        Start-Process 'https://www.python.org/downloads/windows/'
        Read-Host '  Pressione Enter para sair'
        exit 1
    }
    & $winget install --id Python.Python.3.12 -e --scope user --silent --accept-package-agreements --accept-source-agreements | Out-Host
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $py = Find-Python
    if (-not $py) {
        Say '  O Python foi instalado, mas não consegui encontrá-lo. Feche esta janela e rode o instalador de novo.' 'Red'
        Read-Host '  Pressione Enter para sair'
        exit 1
    }
}
$pyw = Join-Path (Split-Path $py) 'pythonw.exe'
if (-not (Test-Path $pyw)) { $pyw = $py }
Say "  Python: $py" 'Gray'

# 2) Para uma versão antiga que esteja aberta
if (Test-Path (Join-Path $app 'spectra')) {
    try { Push-Location $app; & $py -m spectra --parar 2>$null | Out-Null } catch {} finally { Pop-Location }
    Start-Sleep -Seconds 1
}

# 3) Copia o programa
Say '  Copiando o Spectra...'
New-Item -ItemType Directory -Force -Path $app | Out-Null
if (Test-Path (Join-Path $app 'spectra')) { Remove-Item -Recurse -Force (Join-Path $app 'spectra') }
Copy-Item -Recurse -Force (Join-Path $here 'spectra') $app
Copy-Item -Force (Join-Path $here 'spectra.ico') $dest
Copy-Item -Force (Join-Path $here 'desinstalar.ps1') $dest

# 4) Atalhos
$shell = New-Object -ComObject WScript.Shell
function New-Link($path, $target, $arguments, $icon, $desc) {
    $l = $shell.CreateShortcut($path)
    $l.TargetPath = $target
    $l.Arguments = $arguments
    $l.WorkingDirectory = $app
    $l.IconLocation = $icon
    $l.Description = $desc
    $l.Save()
}
$ico = Join-Path $dest 'spectra.ico'
$desktop = [Environment]::GetFolderPath('Desktop')
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'Spectra'
New-Item -ItemType Directory -Force -Path $menu | Out-Null
$run = '-m spectra --janela'
New-Link (Join-Path $desktop 'Spectra.lnk') $pyw $run $ico 'Spectra - tela inicial com seus apps e canais'
New-Link (Join-Path $menu 'Spectra.lnk') $pyw $run $ico 'Spectra - tela inicial com seus apps e canais'
New-Link (Join-Path $menu 'Spectra em tela cheia.lnk') $pyw "$run --tela-cheia" $ico 'Abre o Spectra em tela cheia'
New-Link (Join-Path $menu 'Fechar o Spectra.lnk') $pyw '-m spectra --parar' $ico 'Fecha o Spectra'
$psExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
New-Link (Join-Path $menu 'Desinstalar o Spectra.lnk') $psExe "-ExecutionPolicy Bypass -File `"$dest\desinstalar.ps1`"" $ico 'Remove o Spectra'
# se o "abrir com o Windows" já estava ligado, atualiza o atalho da pasta Inicializar
$startup = Join-Path ([Environment]::GetFolderPath('Startup')) 'Spectra.lnk'
if (Test-Path $startup) { New-Link $startup $pyw $run $ico 'Spectra - tela inicial' }

# 5) Abre
Say '  Abrindo o Spectra...'
try { Push-Location $app; & $pyw -m spectra --parar 2>$null | Out-Null } catch {} finally { Pop-Location }
Start-Process -FilePath (Join-Path $env:SystemRoot 'explorer.exe') -ArgumentList "`"$(Join-Path $menu 'Spectra.lnk')`""
Say ''
Say '  Pronto! O Spectra abre em janela própria.' 'Green'
Say '  Ícone criado na Área de Trabalho e no Menu Iniciar (pasta Spectra).' 'Green'
Say '  Dica: Configurações > Inicialização e janela > Abrir o Spectra junto com o Windows.' 'Gray'
Say ''
Start-Sleep -Seconds 6
