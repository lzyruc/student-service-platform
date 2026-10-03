[CmdletBinding()]
param(
    [string]$ConfigPath,
    [string]$JarPath,
    [switch]$CheckConfig
)

$ErrorActionPreference = 'Stop'

function Read-PrivateValue([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Assert-LocalConfig($Settings) {
    foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'CORS_ALLOWED_ORIGINS')) {
        if ($Settings[$key] -isnot [string] -or [string]::IsNullOrWhiteSpace($Settings[$key])) {
            throw "Fill in $key in local-config.json."
        }
    }
    if ($Settings.DB_USERNAME -eq 'your_mysql_user' -or $Settings.DB_PASSWORD -eq 'your_mysql_password') {
        throw 'Replace the example database credentials in local-config.json.'
    }
    if ($Settings.JWT_SECRET -eq 'replace-with-your-existing-32-byte-or-longer-secret' -or
        [Text.Encoding]::UTF8.GetByteCount($Settings.JWT_SECRET) -lt 32) {
        throw 'JWT_SECRET must be your actual secret, at least 32 UTF-8 bytes.'
    }
    if (-not $Settings.DB_URL.StartsWith('jdbc:mysql://')) {
        throw 'DB_URL must start with jdbc:mysql://.'
    }
}

try {
    # Relative file paths always belong to backend, regardless of the caller's directory.
    if ([string]::IsNullOrWhiteSpace($ConfigPath)) { $ConfigPath = Join-Path $PSScriptRoot 'local-config.json' }
    if (-not [IO.Path]::IsPathRooted($ConfigPath)) { $ConfigPath = Join-Path $PSScriptRoot $ConfigPath }
    $allowedKeys = @(
        'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'CORS_ALLOWED_ORIGINS',
        'ADMIN_BOOTSTRAP_USERNAME', 'ADMIN_BOOTSTRAP_PASSWORD', 'AI_SERVICE_BASE_URL',
        'WARNING_SERVICE_BASE_URL', 'FILE_UPLOAD_DIR'
    )
    $settings = [ordered]@{
        DB_URL = 'jdbc:mysql://127.0.0.1:3306/student_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
        DB_USERNAME = ''
        DB_PASSWORD = ''
        JWT_SECRET = ''
        CORS_ALLOWED_ORIGINS = 'http://localhost:8848,http://127.0.0.1:8848'
    }

    if (Test-Path -LiteralPath $ConfigPath -PathType Leaf) {
        try { $config = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json }
        catch { throw 'Cannot read local-config.json. Check its JSON syntax; do not share its contents.' }
        if ($null -eq $config -or $config -isnot [pscustomobject]) {
            throw 'local-config.json must contain a JSON object.'
        }
        foreach ($entry in $config.PSObject.Properties) {
            if ($allowedKeys -notcontains $entry.Name) { throw "Unknown local setting: $($entry.Name)" }
            if ($entry.Value -isnot [string]) { throw "Setting $($entry.Name) must be a string." }
            $settings[$entry.Name] = $entry.Value
        }
    } else {
        if ($CheckConfig) { throw 'No local-config.json yet. Run start-local.cmd once to configure it.' }
        Write-Host 'First start: save local settings once. Password and JWT input are hidden.'
        foreach ($key in $allowedKeys) {
            $value = [Environment]::GetEnvironmentVariable($key, 'Process')
            if (-not [string]::IsNullOrEmpty($value)) { $settings[$key] = $value }
        }
        if ([string]::IsNullOrWhiteSpace($settings.DB_USERNAME)) {
            $username = Read-Host 'MySQL username [root]'
            $settings.DB_USERNAME = if ([string]::IsNullOrWhiteSpace($username)) { 'root' } else { $username }
        }
        if ([string]::IsNullOrWhiteSpace($settings.DB_PASSWORD)) {
            $settings.DB_PASSWORD = Read-PrivateValue 'MySQL password (the Workbench connection password)'
        }
        if ([string]::IsNullOrWhiteSpace($settings.JWT_SECRET)) {
            Write-Host 'Paste your existing JWT_SECRET to preserve logins.'
            Write-Host 'Leave empty to generate a fixed new secret; old sessions then require login again.'
            $settings.JWT_SECRET = Read-PrivateValue 'Existing JWT_SECRET [Enter = generate]'
            if ([string]::IsNullOrEmpty($settings.JWT_SECRET)) {
                $bytes = New-Object byte[] 48
                $random = [Security.Cryptography.RandomNumberGenerator]::Create()
                try { $random.GetBytes($bytes) } finally { $random.Dispose() }
                $settings.JWT_SECRET = [Convert]::ToBase64String($bytes)
            }
        }
        Assert-LocalConfig $settings
        $json = ConvertTo-Json -InputObject $settings
        [IO.File]::WriteAllText($ConfigPath, $json, (New-Object Text.UTF8Encoding($false)))
        Write-Host "Saved private settings: $ConfigPath"
    }
    Assert-LocalConfig $settings

    if ([string]::IsNullOrWhiteSpace($JarPath)) {
        $latest = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'target') -Recurse -File `
            -Filter 'student-service-platform-*.jar' |
            Where-Object { $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($null -eq $latest) { throw 'No backend JAR found. Run .\mvnw.cmd package first.' }
        $JarPath = $latest.FullName
    } elseif (-not [IO.Path]::IsPathRooted($JarPath)) {
        $JarPath = Join-Path $PSScriptRoot $JarPath
    }
    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) { throw 'The selected backend JAR does not exist.' }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java was not found. Install Java 17 and add it to PATH.' }
    Write-Host "Backend JAR: $JarPath"
    if ($CheckConfig) {
        Write-Host 'Configuration format, Java and JAR checks passed. No service or database connection was started.'
        exit 0
    }

    # Only the Java child receives these settings; restore the caller's environment afterward.
    $previous = @{}
    foreach ($key in $settings.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
    Push-Location -LiteralPath $PSScriptRoot
    try {
        & java -jar $JarPath '--spring.profiles.active=dev'
        $javaExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
    exit $javaExitCode
} catch {
    Write-Host ("Startup failed: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
