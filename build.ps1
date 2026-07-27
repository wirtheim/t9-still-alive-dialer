# Builds Hebrew T9 without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Everything runs offline against the local toolchain.

$ErrorActionPreference = "Stop"

$Root  = $PSScriptRoot
$Tool  = "C:\Users\wirth\pushnote-toolchain"
$Sdk   = "$Tool\sdk"
$Bt    = "$Sdk\build-tools\36.0.0"
$Jdk   = "$Tool\jdk"
$AndroidJar = "$Sdk\platforms\android-36\android.jar"
$Build = "$Root\build"

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"

Remove-Item $Build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$Build\gen","$Build\classes","$Build\dex" | Out-Null

Write-Host "[1/7] compiling resources"
& "$Bt\aapt2.exe" compile --dir "$Root\res" -o "$Build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] linking resources"
& "$Bt\aapt2.exe" link `
    -o "$Build\base.apk" `
    -I $AndroidJar `
    --manifest "$Root\AndroidManifest.xml" `
    -R "$Build\res.zip" `
    --java "$Build\gen" `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] javac"
$sources = @(Get-ChildItem "$Root\src","$Build\gen" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -nowarn `
    -classpath $AndroidJar -d "$Build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] jar"
& "$Jdk\bin\jar.exe" --create --file "$Build\classes.jar" -C "$Build\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "[5/7] d8"
& "$Bt\d8.bat" --lib $AndroidJar --min-api 30 --output "$Build\dex" "$Build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[6/7] packaging"
Copy-Item "$Build\base.apk" "$Build\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$Build\unsigned.apk", "Update")
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, "$Build\dex\classes.dex", "classes.dex") | Out-Null
} finally {
    $zip.Dispose()
}

$keystore = "$Root\debug.keystore"
if (-not (Test-Path $keystore)) {
    Write-Host "      creating debug keystore"
    & "$Jdk\bin\keytool.exe" -genkeypair -v -keystore $keystore `
        -storepass android -keypass android -alias androiddebugkey `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Hebrew T9 Debug, O=Local, C=IL" | Out-Null
}

Write-Host "[7/7] aligning + signing"
& "$Bt\zipalign.exe" -f 4 "$Build\unsigned.apk" "$Build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

& "$Bt\apksigner.bat" sign `
    --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey `
    --out "$Build\hebt9.apk" "$Build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

$size = [math]::Round((Get-Item "$Build\hebt9.apk").Length / 1KB)
Write-Host "OK -> $Build\hebt9.apk ($size KB)"
