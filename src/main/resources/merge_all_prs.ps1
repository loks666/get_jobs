# 设置错误操作
$ErrorActionPreference = "Stop"

# 配置选项
$config = @{
    LogFile = "merge_prs.log"
    DefaultBranch = "main"
    AutoResolveConflicts = $true
    SkipFailedPRs = $false
}

# 日志函数
function Write-Log {
    param($Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] $Message"
    Write-Host $logMessage
    Add-Content -Path $config.LogFile -Value $logMessage
}

# 检查 git 是否已安装
try {
    $gitVersion = git --version
    Write-Log "Git version: $gitVersion"
} catch {
    Write-Log "Error: Git is not installed or not in system PATH"
    exit 1
}

# 检查是否在 git 仓库中
try {
    $gitRoot = git rev-parse --show-toplevel
    Write-Log "Git repository root: $gitRoot"
} catch {
    Write-Log "Error: Current directory is not a Git repository"
    exit 1
}

# 检查工作区状态
$status = git status --porcelain
if ($status) {
    Write-Log "Warning: Working directory has uncommitted changes"
    $response = Read-Host "Continue? (y/n)"
    if ($response -ne "y") {
        Write-Log "Operation cancelled"
        exit 0
    }
}

# 获取当前分支
$currentBranch = git rev-parse --abbrev-ref HEAD
Write-Log "Current branch: $currentBranch"

# 获取所有以 "github/pr/" 开头的远程分支
$prBranches = git branch -r | ForEach-Object { $_.Trim() } | Where-Object { $_ -like "github/pr/*" }

if ($prBranches.Count -eq 0) {
    Write-Log "No PR branches found."
    exit 0
}

Write-Log "Found $($prBranches.Count) PR branches"

# 合并每个 PR
$failedPRs = @()
foreach ($pr in $prBranches) {
    Write-Log "Processing PR: $pr"
    
    try {
        # 尝试合并
        if ($config.AutoResolveConflicts) {
            git merge $pr -X theirs --no-edit
        } else {
            git merge $pr --no-edit
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Successfully merged PR: $pr"
        } else {
            throw "Merge failed"
        }
    } catch {
        Write-Log "Error: Failed to merge $pr : $_"
        $failedPRs += $pr
        
        if (-not $config.SkipFailedPRs) {
            Write-Log "Stopping due to SkipFailedPRs being false"
            break
        }
    }
}

# 输出总结
Write-Log "`nMerge operation summary:"
Write-Log "Total PRs: $($prBranches.Count)"
Write-Log "Successfully merged: $($prBranches.Count - $failedPRs.Count)"
Write-Log "Failed: $($failedPRs.Count)"

if ($failedPRs.Count -gt 0) {
    Write-Log "`nFailed PRs:"
    $failedPRs | ForEach-Object { Write-Log "- $_" }
}

Write-Log "`nOperation completed. Log saved to: $($config.LogFile)"
