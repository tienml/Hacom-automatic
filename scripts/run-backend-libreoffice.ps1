$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\..\backend"
$env:PDF_MODE = "libreoffice"

$defaultLibreOffice = "C:\Program Files\LibreOffice\program\soffice.exe"
if (Test-Path $defaultLibreOffice) {
    $env:LIBREOFFICE_COMMAND = $defaultLibreOffice
}

mvn spring-boot:run
