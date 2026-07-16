$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\..\backend"
$env:PDF_MODE = "disabled"
mvn spring-boot:run
