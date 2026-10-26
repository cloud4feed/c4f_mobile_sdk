<#
.SYNOPSIS
    Copies the native iOS SDK sources into the React Native package (ios/Cloud4FeedSDK).

.DESCRIPTION
    The React Native pod compiles the iOS SDK from this bundled copy. Run this after every
    iOS SDK change and before publishing the npm package.

.EXAMPLE
    .\scripts\sync-ios-sdk.ps1 -IosSdkPath C:\Users\dafat\Desktop\SDK\c4f_mobile_sdk_ios
#>
param(
    [string] $IosSdkPath = (Join-Path $PSScriptRoot "..\..\..\c4f_mobile_sdk_ios")
)

$ErrorActionPreference = "Stop"
$source = Join-Path $IosSdkPath "Sources\Cloud4FeedSDK"
$target = Join-Path $PSScriptRoot "..\ios\Cloud4FeedSDK"

if (-not (Test-Path -LiteralPath $source)) { throw "iOS SDK sources not found: $source" }

if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
Copy-Item -LiteralPath $source -Destination $target -Recurse

$commit = (git -C $IosSdkPath rev-parse --short HEAD 2>$null)
$version = (Select-String -Path (Join-Path $source "SDKConstants.swift") -Pattern 'sdkVersion = "([^"]+)"').Matches[0].Groups[1].Value
"Bundled iOS SDK $version (commit $commit). Do not edit these files here; change c4f_mobile_sdk_ios and run scripts/sync-ios-sdk.ps1." |
    Set-Content -LiteralPath (Join-Path $target "BUNDLED_FROM.txt")

$count = (Get-ChildItem -LiteralPath $target -Recurse -File -Filter *.swift).Count
Write-Host "Copied $count Swift files (iOS SDK $version, commit $commit) to $target"
