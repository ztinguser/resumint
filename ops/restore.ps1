param(
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$DatabaseUser,
    [Parameter(Mandatory=$true)][string]$DatabasePassword,
    [Parameter(Mandatory=$true)][string]$StorageRoot,
    [Parameter(Mandatory=$true)][ValidateSet("RESTORE_TO_EMPTY_TARGET")][string]$ConfirmRestore,
    [string]$DockerContainer,
    [string]$DatabaseHost="127.0.0.1",
    [int]$DatabasePort=3306
)

$ErrorActionPreference="Stop"
$archivePath=(Resolve-Path -LiteralPath $Archive).Path
$targetStorage=[IO.Path]::GetFullPath($StorageRoot)
if((Test-Path -LiteralPath $targetStorage)-and (Get-ChildItem -LiteralPath $targetStorage -Force | Select-Object -First 1)){throw "StorageRoot must be empty before restore."}
$stage=Join-Path ([IO.Path]::GetTempPath()) ("resumint-restore-"+[guid]::NewGuid().ToString("N"));New-Item -ItemType Directory -Path $stage|Out-Null

function Invoke-Captured([string]$file,[string[]]$arguments,[string]$stdin=$null){
    $info=[Diagnostics.ProcessStartInfo]::new($file);$info.UseShellExecute=$false;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    if($null-ne $stdin){$info.RedirectStandardInput=$true}
    foreach($argument in $arguments){$null=$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$null=$process.Start()
    if($null-ne $stdin){$process.StandardInput.Write($stdin);$process.StandardInput.Close()}
    $stdout=$process.StandardOutput.ReadToEnd();$stderr=$process.StandardError.ReadToEnd();$process.WaitForExit()
    if($process.ExitCode-ne 0){throw "$file failed: $stderr"};return $stdout
}
function Invoke-MySql([string[]]$arguments,[string]$stdin=$null){
    if($DockerContainer){return Invoke-Captured "docker" (@("exec","-i","-e","MYSQL_PWD=$DatabasePassword",$DockerContainer,"mysql","-N","-B","-u",$DatabaseUser)+$arguments) $stdin}
    $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$DatabasePassword
    try{return Invoke-Captured "mysql" (@("-N","-B","-h",$DatabaseHost,"-P",[string]$DatabasePort,"-u",$DatabaseUser)+$arguments) $stdin}finally{$env:MYSQL_PWD=$previous}
}
function Invoke-MySqlFile([string[]]$arguments,[string]$inputPath){
    if($DockerContainer){$file="docker";$allArguments=@("exec","-i","-e","MYSQL_PWD=$DatabasePassword",$DockerContainer,"mysql","-u",$DatabaseUser)+$arguments}
    else{$file="mysql";$allArguments=@("-h",$DatabaseHost,"-P",[string]$DatabasePort,"-u",$DatabaseUser)+$arguments;$previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$DatabasePassword}
    try{
        $info=[Diagnostics.ProcessStartInfo]::new($file);$info.UseShellExecute=$false;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
        foreach($argument in $allArguments){$null=$info.ArgumentList.Add($argument)}
        $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$null=$process.Start();$stdoutTask=$process.StandardOutput.ReadToEndAsync();$stderrTask=$process.StandardError.ReadToEndAsync()
        $source=[IO.File]::OpenRead($inputPath);try{$source.CopyTo($process.StandardInput.BaseStream)}finally{$source.Dispose();$process.StandardInput.Close()}
        $process.WaitForExit();$stdout=$stdoutTask.GetAwaiter().GetResult();$stderr=$stderrTask.GetAwaiter().GetResult()
        if($process.ExitCode-ne 0){throw "$file failed: $stderr"};return $stdout
    }finally{if(-not $DockerContainer){$env:MYSQL_PWD=$previous}}
}
try{
    if(Test-Path -LiteralPath "$archivePath.sha256"){
        $expected=((Get-Content -LiteralPath "$archivePath.sha256" -Raw).Trim().Split(" ")[0]).ToLowerInvariant();$actual=(Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if($expected-ne $actual){throw "Archive checksum does not match its sidecar."}
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $stage
    $manifest=Get-Content -LiteralPath (Join-Path $stage "manifest.json") -Raw|ConvertFrom-Json
    if($manifest.formatVersion-ne 1-or $manifest.consistency-ne "api-and-worker-stopped"){throw "Unsupported or inconsistent backup manifest."}
    $databaseSql=Join-Path $stage "database.sql";$databaseHash=(Get-FileHash -LiteralPath $databaseSql -Algorithm SHA256).Hash.ToLowerInvariant()
    if($databaseHash-ne $manifest.databaseSha256){throw "Database dump checksum mismatch."}
    $stagedStorage=(Resolve-Path -LiteralPath (Join-Path $stage "storage")).Path
    foreach($file in $manifest.storageFiles){
        $path=[IO.Path]::GetFullPath((Join-Path $stagedStorage $file.path))
        if(-not $path.StartsWith($stagedStorage+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Backup manifest contains an unsafe path."}
        if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Backup storage file is missing: $($file.path)"}
        if((Get-Item -LiteralPath $path).Length-ne $file.bytes){throw "Backup storage size mismatch: $($file.path)"}
        if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()-ne $file.sha256){throw "Backup storage checksum mismatch: $($file.path)"}
    }
    $tableCount=[int](Invoke-MySql @("-e","SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';") ).Trim()
    if($tableCount-ne 0){throw "Target database must be empty before restore."}
    Invoke-MySqlFile @($Database) $databaseSql|Out-Null
    New-Item -ItemType Directory -Force -Path $targetStorage|Out-Null
    Get-ChildItem -LiteralPath (Join-Path $stage "storage") -Force|Copy-Item -Destination $targetStorage -Recurse -Force
    $migration=(Invoke-MySql @($Database,"-e","SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;")).Trim()
    $restoredFiles=@(Get-ChildItem -LiteralPath $targetStorage -File -Recurse).Count
    [ordered]@{database=$Database;migrationVersion=$migration;storageFiles=$restoredFiles;verifiedAtUtc=[DateTime]::UtcNow.ToString("o")}|ConvertTo-Json
}finally{
    if((Test-Path -LiteralPath $stage)-and $stage.StartsWith([IO.Path]::GetTempPath(),[StringComparison]::OrdinalIgnoreCase)){Remove-Item -LiteralPath $stage -Recurse -Force}
}
