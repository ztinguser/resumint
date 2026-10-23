param(
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$DatabaseUser,
    [Parameter(Mandatory=$true)][string]$DatabasePassword,
    [Parameter(Mandatory=$true)][string]$StorageRoot,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$DockerContainer,
    [string]$DatabaseHost="127.0.0.1",
    [int]$DatabasePort=3306,
    [switch]$ApplicationStopped
)

$ErrorActionPreference="Stop"
if(-not $ApplicationStopped){throw "Stop both API and Worker, then pass -ApplicationStopped."}
$storage=(Resolve-Path -LiteralPath $StorageRoot).Path
if(-not (Test-Path -LiteralPath $storage -PathType Container)){throw "StorageRoot must be an existing directory."}
$output=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output | Out-Null
$stage=Join-Path ([IO.Path]::GetTempPath()) ("resumint-backup-"+[guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $stage | Out-Null

function Invoke-ToFile([string]$file,[string[]]$arguments,[string]$outputPath){
    $info=[Diagnostics.ProcessStartInfo]::new($file);$info.UseShellExecute=$false;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in $arguments){$null=$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$null=$process.Start();$stderrTask=$process.StandardError.ReadToEndAsync()
    $destination=[IO.File]::Create($outputPath)
    try{$process.StandardOutput.BaseStream.CopyTo($destination)}finally{$destination.Dispose()}
    $process.WaitForExit();$stderr=$stderrTask.GetAwaiter().GetResult()
    if($process.ExitCode-ne 0){throw "$file failed: $stderr"}
}
try{
    $dumpArguments=@("--single-transaction","--routines","--events","--hex-blob","--no-tablespaces","--set-gtid-purged=OFF","--default-character-set=utf8mb4","-u",$DatabaseUser,$Database)
    $databaseSql=Join-Path $stage "database.sql"
    if($DockerContainer){Invoke-ToFile "docker" (@("exec","-e","MYSQL_PWD=$DatabasePassword",$DockerContainer,"mysqldump")+$dumpArguments) $databaseSql}
    else{
        $previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$DatabasePassword
        try{Invoke-ToFile "mysqldump" (@("-h",$DatabaseHost,"-P",[string]$DatabasePort)+$dumpArguments) $databaseSql}finally{$env:MYSQL_PWD=$previous}
    }
    $utf8=[Text.UTF8Encoding]::new($false)
    $storageCopy=Join-Path $stage "storage";New-Item -ItemType Directory -Path $storageCopy | Out-Null
    Get-ChildItem -LiteralPath $storage -Force | Copy-Item -Destination $storageCopy -Recurse -Force
    $files=Get-ChildItem -LiteralPath $storageCopy -File -Recurse | Sort-Object FullName | ForEach-Object {
        [ordered]@{path=$_.FullName.Substring($storageCopy.Length+1).Replace("\","/");sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$_.Length}
    }
    $manifest=[ordered]@{formatVersion=1;createdAtUtc=[DateTime]::UtcNow.ToString("o");database=$Database;consistency="api-and-worker-stopped";databaseSha256=(Get-FileHash -LiteralPath (Join-Path $stage "database.sql") -Algorithm SHA256).Hash.ToLowerInvariant();storageFiles=@($files)}
    [IO.File]::WriteAllText((Join-Path $stage "manifest.json"),($manifest|ConvertTo-Json -Depth 6),$utf8)
    $stamp=[DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ");$archive=Join-Path $output "resumint-backup-$stamp.zip"
    Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $archive -CompressionLevel Optimal
    $archiveHash=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$archive.sha256","$archiveHash  $([IO.Path]::GetFileName($archive))`n",$utf8)
    [ordered]@{archive=$archive;sha256=$archiveHash;databaseBytes=(Get-Item (Join-Path $stage "database.sql")).Length;storageFileCount=@($files).Count}|ConvertTo-Json
}finally{
    if((Test-Path -LiteralPath $stage)-and $stage.StartsWith([IO.Path]::GetTempPath(),[StringComparison]::OrdinalIgnoreCase)){Remove-Item -LiteralPath $stage -Recurse -Force}
}
