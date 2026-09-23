# package-msix.ps1
# PowerShell script to package TimeLiner into TimeLiner.msix using Windows SDK MakeAppx tool

param (
    [string]$SourceDir = "build\TimeLiner",
    [string]$OutputFile = "TimeLiner.msix",
    [switch]$SignPackage,
    [string]$CertPublisher = "CN=93C777BB-7D35-46D0-A696-DA92A3C84C52"
)

$ErrorActionPreference = "Stop"

Write-Host "==> Searching for makeappx.exe..." -ForegroundColor Cyan

$makeappxPath = Get-Command makeappx -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path
if (-not $makeappxPath) {
    $sdkPaths = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\makeappx.exe" -ErrorAction SilentlyContinue
    if ($sdkPaths) {
        $makeappxPath = $sdkPaths[-1].FullName
    }
}

if (-not $makeappxPath -or -not (Test-Path $makeappxPath)) {
    Write-Error "makeappx.exe not found! Please ensure Windows 10/11 SDK is installed."
    exit 1
}

Write-Host "Found makeappx at: $makeappxPath" -ForegroundColor Green

if (-not (Test-Path $SourceDir)) {
    Write-Error "Source directory '$SourceDir' does not exist."
    exit 1
}

# Sanitize PE binaries to fix corrupted certificate tables (common JDK/jpackage bug with Eclipse Adoptium)
function Repair-PEHeaders {
    param ([string]$Directory)

    $peFiles = Get-ChildItem -Recurse -Path $Directory -Include "*.exe", "*.dll"
    foreach ($file in $peFiles) {
        if ($file.IsReadOnly) {
            Set-ItemProperty $file.FullName -Name IsReadOnly -Value $false
        }

        try {
            $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
            if ($bytes.Length -lt 0x40) { continue }
            $e_lfanew = [BitConverter]::ToInt32($bytes, 0x3C)
            if ($e_lfanew + 4 -gt $bytes.Length) { continue }
            $peSig = [System.Text.Encoding]::ASCII.GetString($bytes, $e_lfanew, 4)
            if ($peSig -ne "PE`0`0") { continue }

            $magic = [BitConverter]::ToUInt16($bytes, $e_lfanew + 0x18)
            $secDirOffset = if ($magic -eq 0x20B) { $e_lfanew + 0x18 + 112 + 4 * 8 } elseif ($magic -eq 0x10B) { $e_lfanew + 0x18 + 96 + 4 * 8 } else { continue }
            $secRva = [BitConverter]::ToUInt32($bytes, $secDirOffset)
            $secSize = [BitConverter]::ToUInt32($bytes, $secDirOffset + 4)

            if ($secSize -gt 0) {
                $isMalformed = $false
                if ($secRva + 8 -gt $bytes.Length) {
                    $isMalformed = $true
                } else {
                    $dwLen = [BitConverter]::ToUInt32($bytes, $secRva)
                    $wRev = [BitConverter]::ToUInt16($bytes, $secRva + 4)
                    $wType = [BitConverter]::ToUInt16($bytes, $secRva + 6)
                    if ($dwLen -gt $secSize -or $dwLen -lt 8 -or ($wRev -ne 0x0200 -and $wRev -ne 0x0100) -or $wType -ne 0x0002) {
                        $isMalformed = $true
                    }
                }

                if ($isMalformed) {
                    Write-Host "==> Repairing malformed PE certificate table in $($file.Name)..." -ForegroundColor Yellow
                    [BitConverter]::GetBytes([uint32]0).CopyTo($bytes, $secDirOffset)
                    [BitConverter]::GetBytes([uint32]0).CopyTo($bytes, $secDirOffset + 4)
                    [System.IO.File]::WriteAllBytes($file.FullName, $bytes)
                }
            }
        } catch {
            Write-Warning "Could not inspect $($file.FullName): $_"
        }
    }
}

Repair-PEHeaders -Directory $SourceDir

Write-Host "==> Packaging '$SourceDir' into '$OutputFile'..." -ForegroundColor Cyan
& $makeappxPath pack /d $SourceDir /p $OutputFile /o

if ($LASTEXITCODE -eq 0) {
    Write-Host "==> Successfully created $OutputFile!" -ForegroundColor Green
} else {
    Write-Error "Packaging failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

if ($SignPackage) {
    Write-Host "==> Searching for signtool.exe..." -ForegroundColor Cyan
    $signtoolPath = Get-Command signtool -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path
    if (-not $signtoolPath) {
        $sdkSignPaths = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue
        if ($sdkSignPaths) {
            $signtoolPath = $sdkSignPaths[-1].FullName
        }
    }

    if (-not $signtoolPath -or -not (Test-Path $signtoolPath)) {
        Write-Error "signtool.exe not found!"
        exit 1
    }

    $pfxFile = "TimeLinerDevCert.pfx"
    $certPassword = "TimeLinerPassword123!"

    if (-not (Test-Path $pfxFile)) {
        Write-Host "==> Creating self-signed certificate for publisher '$CertPublisher'..." -ForegroundColor Yellow
        $cert = New-SelfSignedCertificate -Type Custom -Subject $CertPublisher `
            -KeyUsage DigitalSignature -FriendlyName "TimeLiner Development Certificate" `
            -CertStoreLocation "Cert:\CurrentUser\My" `
            -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3")

        $pwd = ConvertTo-SecureString -String $certPassword -Force -AsPlainText
        Export-PfxCertificate -Cert $cert -FilePath $pfxFile -Password $pwd | Out-Null
        Write-Host "Created self-signed certificate and exported to $pfxFile" -ForegroundColor Green
    }

    Write-Host "==> Signing $OutputFile..." -ForegroundColor Cyan
    & $signtoolPath sign /fd SHA256 /a /f $pfxFile /p $certPassword $OutputFile

    if ($LASTEXITCODE -eq 0) {
        Write-Host "==> Successfully signed $OutputFile!" -ForegroundColor Green
    } else {
        Write-Warning "Signing failed. Note: Signing MSIX files with signtool requires registering appxsip.dll via Admin PowerShell:"
        Write-Warning "regsvr32 ""C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\appxsip.dll"""
    }
}
