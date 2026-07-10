# publish-wiki.ps1
#
# One-shot uploader: takes every .md file in docs\wiki\ and publishes it to
# this repository's GitHub Wiki. Overwrites existing pages of the same name.
#
# GitHub wikis are separate git repos at <repo>.wiki.git. They come into
# existence the first time a page is created. THIS SCRIPT WILL FAIL if the
# wiki has never been initialized - GitHub returns "repository not found"
# on the .wiki.git URL until at least one page exists.
#
# ONE-TIME MANUAL STEP before the first run:
#   1. In a browser, open:
#        https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/wiki
#   2. Click "Create the first page".
#   3. Save it with any placeholder content (this script will overwrite it).
# After that, this script can be re-run any number of times to sync
# updated pages from docs\wiki\ into the wiki.

[CmdletBinding()]
param(
    [string] $Repo      = "mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-",
    [string] $Source    = (Join-Path $PSScriptRoot "..\docs\wiki"),
    [string] $CloneRoot = $env:TEMP
)

$ErrorActionPreference = "Stop"

$wikiUrl = "https://github.com/$Repo.wiki.git"
$workDir = Join-Path $CloneRoot "oauth2-jwt-client-wiki-$(Get-Random)"

if (-not (Test-Path $Source)) {
    throw "Source directory not found: $Source"
}
$pages = Get-ChildItem $Source -Filter "*.md" | Sort-Object Name
if ($pages.Count -eq 0) {
    throw "No .md files in $Source"
}

Write-Host "==> Cloning wiki: $wikiUrl" -ForegroundColor Cyan
git clone $wikiUrl $workDir 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Wiki clone failed. Most likely cause: wiki has not been initialized." -ForegroundColor Red
    Write-Host "Fix: open https://github.com/$Repo/wiki in a browser and click 'Create the first page'." -ForegroundColor Yellow
    Write-Host "     Save any placeholder content, then re-run this script." -ForegroundColor Yellow
    throw "wiki clone failed"
}

Write-Host "==> Copying $($pages.Count) pages into wiki working tree" -ForegroundColor Cyan
foreach ($page in $pages) {
    $dest = Join-Path $workDir $page.Name
    Copy-Item $page.FullName $dest -Force
    Write-Host "    $($page.Name)"
}

Push-Location $workDir
try {
    git add -A | Out-Null
    $status = git status --porcelain
    if (-not $status) {
        Write-Host "==> No changes - wiki is already in sync." -ForegroundColor Green
        exit 0
    }

    Write-Host "==> Committing" -ForegroundColor Cyan
    git commit -m "sync wiki pages from docs/wiki/" | Out-Null

    Write-Host "==> Pushing to $wikiUrl" -ForegroundColor Cyan
    git push origin master 2>&1 -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0) {
        # older wikis use 'master', newer may use 'main' - try both
        git push origin main
    }
} finally {
    Pop-Location
}

Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Wiki updated. View at:" -ForegroundColor Green
Write-Host "  https://github.com/$Repo/wiki" -ForegroundColor Green
