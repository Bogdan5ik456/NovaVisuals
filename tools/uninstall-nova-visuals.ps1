# Повне видалення Nova Visuals: процеси, програма, дані гри, налаштування, ярлики й записи в реєстрі.
# Працює, навіть коли звичайний деінсталятор пише «не знайдено».
$ErrorActionPreference = 'SilentlyContinue'

Get-Process | Where-Object { $_.ProcessName -like 'Nova Visuals*' -or $_.ProcessName -eq 'NovaVisuals' } | Stop-Process -Force
Start-Sleep -Milliseconds 800

$paths = @(
    "$env:LOCALAPPDATA\Programs\Nova Visuals",
    "$env:LOCALAPPDATA\Programs\nova-visuals-launcher",
    "$env:ProgramFiles\Nova Visuals",
    "${env:ProgramFiles(x86)}\Nova Visuals",
    "$env:APPDATA\.nova-visuals",
    "$env:APPDATA\Nova Visuals",
    "$env:APPDATA\nova-visuals-launcher",
    "$env:LOCALAPPDATA\nova-visuals-launcher-updater",
    "$env:LOCALAPPDATA\Nova Visuals",
    "$env:USERPROFILE\Desktop\Nova Visuals.lnk",
    "$env:PUBLIC\Desktop\Nova Visuals.lnk",
    "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Nova Visuals.lnk",
    "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Nova Visuals.lnk"
)
foreach ($p in $paths) {
    if (Test-Path -LiteralPath $p) {
        Remove-Item -LiteralPath $p -Recurse -Force
        if (Test-Path -LiteralPath $p) { Write-Host "Не вдалося видалити: $p" -ForegroundColor Yellow }
        else { Write-Host "Видалено: $p" -ForegroundColor Green }
    }
}

# Записи в «Програмах і засобах» (у тому числі зламані, що ведуть на неіснуючий деінсталятор)
foreach ($hive in 'HKCU:', 'HKLM:') {
    foreach ($root in "$hive\Software\Microsoft\Windows\CurrentVersion\Uninstall", "$hive\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall") {
        Get-ChildItem $root | ForEach-Object {
            $dn = (Get-ItemProperty -LiteralPath $_.PSPath).DisplayName
            if ($dn -eq 'Nova Visuals') {
                Remove-Item -LiteralPath $_.PSPath -Recurse -Force
                Write-Host "Запис реєстру видалено: $($_.PSChildName)" -ForegroundColor Green
            }
        }
    }
}
foreach ($k in 'HKCU:\Software\com.novavisuals.launcher', 'HKCU:\Software\Nova Visuals') {
    if (Test-Path -LiteralPath $k) { Remove-Item -LiteralPath $k -Recurse -Force }
}

Write-Host ''
Write-Host 'Готово: Nova Visuals повністю видалено. Нову версію можна ставити з нуля.' -ForegroundColor Cyan
