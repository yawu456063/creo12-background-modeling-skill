[CmdletBinding()]
param([string]$ArchivePath)
$ErrorActionPreference = 'Stop'
$skill = Split-Path $PSScriptRoot -Parent
$books = Join-Path $skill 'assets\books'
$meta = Get-Content -LiteralPath (Join-Path $books 'book.json') -Raw -Encoding utf8 | ConvertFrom-Json
$target = Join-Path $books $meta.file
if (Test-Path -LiteralPath $target) {
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $meta.sha256) {
        throw 'Existing book hash differs; preserve it and inspect before replacing.'
    }
    Write-Output "Verified book: $target"
    return
}
$download = $null
$pending = $null
try {
    if (-not $ArchivePath) {
        $download = Join-Path ([IO.Path]::GetTempPath()) (([guid]::NewGuid().ToString()) + '.zip')
        Invoke-WebRequest -Uri $meta.release_archive -OutFile $download
        $ArchivePath = $download
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($ArchivePath))
    try {
        $entryName = 'creo12-background-modeling-skill/skills/creo12-background-modeling/assets/books/' + $meta.file
        $entry = $archive.GetEntry($entryName)
        if (-not $entry) { throw 'Book is missing from this archive.' }
        $pending = Join-Path $books (([guid]::NewGuid().ToString()) + '.partial')
        $inputStream = $entry.Open()
        $outputStream = [IO.File]::Create($pending)
        try { $inputStream.CopyTo($outputStream) }
        finally { $inputStream.Dispose(); $outputStream.Dispose() }
    } finally { $archive.Dispose() }
    if ((Get-Item -LiteralPath $pending).Length -ne $meta.bytes -or
        (Get-FileHash -LiteralPath $pending -Algorithm SHA256).Hash.ToLowerInvariant() -ne $meta.sha256) {
        throw 'Downloaded book failed size or SHA-256 verification.'
    }
    Move-Item -LiteralPath $pending -Destination $target
    Write-Output "Installed verified book: $target"
} finally {
    if ($pending -and (Test-Path -LiteralPath $pending)) { Remove-Item -LiteralPath $pending }
    if ($download -and (Test-Path -LiteralPath $download)) { Remove-Item -LiteralPath $download }
}
