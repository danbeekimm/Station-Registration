# 측정소 시뮬레이터를 데이터 파일 수만큼 띄운다. 파일 하나가 측정소 하나다.
# Windows의 cmd·PowerShell 공용이며 run-stations.cmd가 이 파일을 실행한다(Git Bash에서는 run-stations.sh).
#
#   run-stations.cmd                        data\*.json 전체, 1초 간격
#   run-stations.cmd 10                     10ms 간격(빠른 검증, 한 달치가 약 8초)
#   run-stations.cmd 1000 data\은평구.json   특정 파일만
#
# 환경변수(.env가 있으면 자동으로 읽는다): DUST_SERVER(기본 localhost:${DUST_TCP_PORT:-9000}), STATION_TOKEN_<측정소코드>
param(
    [int]$IntervalMs = 1000,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Files = @()
)
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

if ($Files.Count -eq 0) { $Files = @(Get-ChildItem -Path data -Filter *.json | ForEach-Object { $_.FullName }) }
$server = if ($env:DUST_SERVER) { $env:DUST_SERVER } else { 'localhost:' + $(if ($env:DUST_TCP_PORT) { $env:DUST_TCP_PORT } else { '9000' }) }

# 개발용 토큰. db/seed.sql의 초기 측정소와 같은 값이며 의도적으로 저장소에 둔다.
# 은평구(111191)는 db/seed.sql에 없어 처음 실행하면 UNKNOWN_STATION으로 거부된다.
# registerStation으로 등록한 뒤 다시 실행하면 서버 수정·재기동 없이 수신된다.
$defaultTokens = @{
    'STATION_TOKEN_111311' = 'dev-token-nowon-111311'
    'STATION_TOKEN_111121' = 'dev-token-junggu-111121'
    'STATION_TOKEN_111212' = 'dev-token-gangseo-111212'
    'STATION_TOKEN_111191' = 'dev-token-eunpyeong-111191'
}
foreach ($name in $defaultTokens.Keys) {
    if (-not [Environment]::GetEnvironmentVariable($name)) { [Environment]::SetEnvironmentVariable($name, $defaultTokens[$name]) }
}

Write-Host '시뮬레이터 빌드 확인 중...'
& .\gradlew.bat -q :station-simulator:installDist
if ($LASTEXITCODE -ne 0) { Write-Host '빌드 실패'; exit 1 }

$bin = Join-Path $PSScriptRoot 'station-simulator\build\install\station-simulator\bin\station-simulator.bat'
$procs = @(); $names = @()
foreach ($file in $Files) {
    $p = Start-Process -FilePath $bin -ArgumentList @('--file', "`"$file`"", '--server', $server, '--interval-ms', $IntervalMs) -NoNewWindow -PassThru
    $null = $p.Handle   # 핸들을 잡아 두어야 종료 뒤 ExitCode를 읽을 수 있다
    $procs += $p; $names += [System.IO.Path]::GetFileNameWithoutExtension($file)
}
Write-Host "측정소 $($procs.Count)개 기동 (서버 $server, 간격 ${IntervalMs}ms). 중단: Ctrl-C"

$failed = 0
for ($i = 0; $i -lt $procs.Count; $i++) {
    $procs[$i].WaitForExit()
    switch ($procs[$i].ExitCode) {
        0 { Write-Host "[$($names[$i])] 완료" }
        2 { Write-Host "[$($names[$i])] 서버가 측정소를 거부함 (미등록 또는 토큰 불일치, 종료 코드 2)"; $failed = 1 }
        default { Write-Host "[$($names[$i])] 비정상 종료 (코드 $($procs[$i].ExitCode))"; $failed = 1 }
    }
}
exit $failed
