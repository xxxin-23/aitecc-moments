# AITECC Moments - open LAN access
# Right-click this file -> "Run with PowerShell" as Administrator.
# It allows other devices on the same WiFi / campus network to open the site.

$ruleName = "AITECC Moments 8080"
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue

if ($existing) {
    Write-Host "Firewall rule already exists: $ruleName"
} else {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -LocalPort 8080 `
        -Protocol TCP -Action Allow -Profile Any | Out-Null
    Write-Host "Firewall rule added: inbound TCP 8080 allowed."
}

Write-Host ""
Write-Host "Share these links with club members (same WiFi only):"
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -notlike '*VMware*' } |
    ForEach-Object { Write-Host ("  http://" + $_.IPAddress + ":8080") }
Write-Host ""
Write-Host "Keep the app running (java -jar target/aitecc-moments.jar --server.port=8080 --server.address=0.0.0.0)."
Write-Host ""
Read-Host "Press Enter to close"
