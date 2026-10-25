# 중앙 서버를 띄운다. Windows의 cmd·PowerShell 공용이며 run-server.cmd가 이 파일을 실행한다(Git Bash에서는 run-server.sh).
# 같은 디렉터리의 .env가 있으면 읽어서 환경변수로 넘긴다.
#
#   run-server.cmd                                   기본 설정(또는 .env)으로 기동
#   set SLACK_WEBHOOK_URL=https://hooks.slack.com/...     (cmd)         뒤에 run-server.cmd
#   $env:SLACK_WEBHOOK_URL="https://hooks.slack.com/..."  (PowerShell)  뒤에 .\run-server.cmd
#   run-server.cmd --args="--dust.slack.webhook-url=https://hooks.slack.com/..."   Gradle bootRun 인자 전달
#
# .env 없이 gradlew.bat :central-server:bootRun 을 직접 실행해도 같다. 이 스크립트는 .env 로딩만 더한다.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# 콘솔을 UTF-8(코드 페이지 65001)로 바꾼다. JVM이 UTF-8로 내보내는 한글이 CP949 콘솔에서 깨지지 않게 한다.
# 콘솔 설정이라 스크립트가 끝난 뒤에도 그 터미널에 유지된다.
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# JDK 24 이상으로 Gradle을 실행하면 네이티브 접근 WARNING 4줄이 먼저 찍힌다. 동작과 무관하므로 끈다.
$env:GRADLE_OPTS = ("--enable-native-access=ALL-UNNAMED " + $env:GRADLE_OPTS).Trim()

# .env 파일의 값은 이미 설정된 환경변수보다 우선하지 않는다(쉘에서 준 값이 이긴다)
if (Test-Path .env) {
    foreach ($raw in Get-Content .env -Encoding UTF8) {
        $line = ($raw -split '#', 2)[0].Trim()
        if (-not $line -or $line -notmatch '=') { continue }
        $key, $value = $line -split '=', 2
        $key = $key.Trim(); $value = $value.Trim().Trim('"')   # 양쪽 큰따옴표는 벗긴다
        if (-not [Environment]::GetEnvironmentVariable($key)) { [Environment]::SetEnvironmentVariable($key, $value) }
    }
}

function Get-Setting($name, $default) { $v = [Environment]::GetEnvironmentVariable($name); if ($v) { $v } else { $default } }
$slack = if (Get-Setting 'SLACK_WEBHOOK_URL' '') { '설정됨' } else { '미설정' }
Write-Host ("중앙 서버 기동: HTTP {0}, TCP {1}, DB {2}:{3}/{4}, Slack {5}" -f (Get-Setting 'DUST_HTTP_PORT' '8080'), (Get-Setting 'DUST_TCP_PORT' '9000'), (Get-Setting 'DUST_DB_HOST' 'localhost'), (Get-Setting 'DUST_DB_PORT' '5432'), (Get-Setting 'DUST_DB_NAME' 'dust'), $slack)
& .\gradlew.bat :central-server:bootRun @args
exit $LASTEXITCODE
