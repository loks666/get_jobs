<#
    kill-services.ps1
    用途：一键终止前端（6866）与后端（8888）占用端口的进程
    使用：
      - 直接运行：  powershell -ExecutionPolicy Bypass -File ./bin/kill-services.ps1
      - 指定端口：  powershell -ExecutionPolicy Bypass -File ./bin/kill-services.ps1 -Ports 6866,8888
#>

[CmdletBinding()]
param(
    [int[]]$Ports = @(6866, 8888)
)

Write-Host "Killing services on ports: $($Ports -join ', ')" -ForegroundColor Yellow

$killed = @()

foreach ($port in $Ports) {
    Write-Host "Checking port $port..."

    # 使用 netstat 查找监听中的进程
    $lines = cmd /c "netstat -ano | findstr LISTENING | findstr :$port" 2>$null

    if (-not $lines) {
        Write-Host "No LISTENING process found on :$port" -ForegroundColor DarkGray
        continue
    }

    $pids = @()
    foreach ($line in $lines) {
        # 示例行：TCP    0.0.0.0:6866  0.0.0.0:0  LISTENING  36780
        $tokens = $line -split '\s+'
        if ($tokens.Count -ge 5) {
            $pidToken = $tokens[$tokens.Count - 1]
            if ($pidToken -match '^\d+$') {
                $pids += [int]$pidToken
            }
        }
    }

    $pids = $pids | Select-Object -Unique

    if ($pids.Count -eq 0) {
        Write-Host "Found lines but no PID parsed for :$port" -ForegroundColor Yellow
        continue
    }

    foreach ($procId in $pids) {
        Write-Host "Killing PID $procId (port :$port)..." -ForegroundColor Cyan
        try {
            cmd /c "taskkill /PID $procId /F" | Out-Null
            $killed += $procId
            Write-Host "SUCCESS: Killed PID $procId" -ForegroundColor Green
        } catch {
            Write-Warning ("Failed to kill PID {0}: {1}" -f $procId, $_.Exception.Message)
        }
    }
}

if ($killed.Count -eq 0) {
    Write-Host "No processes were killed." -ForegroundColor DarkYellow
} else {
    $unique = $killed | Select-Object -Unique
    $list = $unique -join ', '
    Write-Host "Killed PIDs: $list" -ForegroundColor Green
}