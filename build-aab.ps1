# Builds a Play-ready Android App Bundle without Gradle.
#
# aapt2 --proto-format -> restructure into the bundle module layout -> bundletool
# build-bundle -> jarsigner with the upload key.
#
# Play requires an .aab for new apps; the .apk from build.ps1 is for sideloading only.

$ErrorActionPreference = "Stop"

$Root  = $PSScriptRoot
$Tool  = "C:\Users\wirth\pushnote-toolchain"
$Sdk   = "$Tool\sdk"
$Bt    = "$Sdk\build-tools\36.0.0"
$Jdk   = "$Tool\jdk"
$AndroidJar = "$Sdk\platforms\android-36\android.jar"
$BundleTool = (Get-ChildItem "$Tool\bundletool" -Filter "bundletool-all-*.jar" |
               Sort-Object Name -Descending | Select-Object -First 1).FullName
$Out   = "$Root\build-aab"

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"

Remove-Item $Out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$Out\gen","$Out\classes","$Out\dex","$Out\module" | Out-Null

Write-Host "[1/8] compiling resources"
& "$Bt\aapt2.exe" compile --dir "$Root\res" -o "$Out\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/8] linking resources (proto format)"
& "$Bt\aapt2.exe" link `
    --proto-format `
    -o "$Out\base-proto.apk" `
    -I $AndroidJar `
    --manifest "$Root\AndroidManifest.xml" `
    -R "$Out\res.zip" `
    --java "$Out\gen" `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/8] javac"
$sources = @(Get-ChildItem "$Root\src","$Out\gen" -Filter *.java -Recurse |
             Select-Object -ExpandProperty FullName)
& "$Jdk\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -nowarn `
    -classpath $AndroidJar -d "$Out\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/8] d8"
& "$Jdk\bin\jar.exe" --create --file "$Out\classes.jar" -C "$Out\classes" .
& "$Bt\d8.bat" --lib $AndroidJar --min-api 30 --output "$Out\dex" "$Out\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/8] restructuring into bundle module layout"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$extract = "$Out\extracted"
[System.IO.Compression.ZipFile]::ExtractToDirectory("$Out\base-proto.apk", $extract)

$mod = "$Out\module"
New-Item -ItemType Directory -Force -Path "$mod\manifest","$mod\dex" | Out-Null
Move-Item "$extract\AndroidManifest.xml" "$mod\manifest\AndroidManifest.xml"
Move-Item "$extract\resources.pb"        "$mod\resources.pb"
if (Test-Path "$extract\res") { Move-Item "$extract\res" "$mod\res" }
Copy-Item "$Out\dex\classes.dex" "$mod\dex\classes.dex"

Write-Host "[6/8] zipping module"
$moduleZip = "$Out\base.zip"
$zip = [System.IO.Compression.ZipFile]::Open($moduleZip, "Create")
try {
    foreach ($f in Get-ChildItem $mod -Recurse -File) {
        $rel = $f.FullName.Substring($mod.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $rel) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host "[7/8] bundletool build-bundle"
& "$Jdk\bin\java.exe" -jar $BundleTool build-bundle `
    --modules $moduleZip --output "$Out\unsigned.aab"
if ($LASTEXITCODE -ne 0) { throw "bundletool failed" }

Write-Host "[8/8] signing with the upload key"
$keystore = "$Root\release.keystore"
if (-not (Test-Path $keystore)) {
    Write-Host "      creating release keystore -- BACK THIS FILE UP"
    & "$Jdk\bin\keytool.exe" -genkeypair -v -keystore $keystore `
        -storepass wirtheim -keypass wirtheim -alias upload `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Wirtheim, O=W-are-theim, C=IL" | Out-Null
}
Copy-Item "$Out\unsigned.aab" "$Out\t9-still-alive-dialer.aab" -Force
& "$Jdk\bin\jarsigner.exe" -keystore $keystore -storepass wirtheim -keypass wirtheim `
    -sigalg SHA256withRSA -digestalg SHA-256 `
    "$Out\t9-still-alive-dialer.aab" upload | Out-Null
if ($LASTEXITCODE -ne 0) { throw "jarsigner failed" }

$size = [math]::Round((Get-Item "$Out\t9-still-alive-dialer.aab").Length / 1KB)
Write-Host "OK -> $Out\t9-still-alive-dialer.aab ($size KB)"
