Write-Host "Starting Job Management System (Backend + Frontend)..." -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\backend'; .\mvnw.cmd spring-boot:run"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm install; npm run dev"

Write-Host "Backend API:  http://localhost:8080 (Swagger UI: http://localhost:8080/swagger-ui.html)" -ForegroundColor Green
Write-Host "Frontend App: http://localhost:5173" -ForegroundColor Green
