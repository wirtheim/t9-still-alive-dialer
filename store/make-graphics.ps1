# Renders the Play Store graphics with GDI+.
#
#   feature-graphic.png   1024x500, no alpha  (Play requirement)
#   icon-512.png           512x512, no alpha  (Play requirement)
#
# Both are opaque 24-bit RGB: Play rejects alpha channels on these two assets.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$Out = $PSScriptRoot

$Indigo   = [System.Drawing.Color]::FromArgb(91, 75, 232)
$IndigoHi = [System.Drawing.Color]::FromArgb(124, 108, 255)
$Ink      = [System.Drawing.Color]::FromArgb(14, 14, 22)
$White    = [System.Drawing.Color]::White
$Muted    = [System.Drawing.Color]::FromArgb(168, 168, 190)

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    return @($bmp, $g)
}

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x,          $y,          $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y,          $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0,   90)
    $p.AddArc($x,          $y + $h - $d, $d, $d, 90,  90)
    $p.CloseFigure()
    return $p
}

# Minimal SVG path reader -- enough for the subset used by res/drawable/ic_logo.xml
# (M, C/c, L/l, H/h, V/v, Z). Reusing the app's own path data is what keeps the store
# icon and the launcher icon identical instead of merely similar.
function Convert-SvgPath([string]$d, [float]$scale, [float]$tx, [float]$ty) {
    # Pass 1: split into (command, numbers) segments.
    $segments = New-Object System.Collections.Generic.List[object]
    $cmd = ''
    $nums = New-Object System.Collections.Generic.List[float]
    foreach ($t in [regex]::Matches($d, '([MmCcLlHhVvZz])|(-?\d*\.?\d+)')) {
        if ($t.Groups[1].Success) {
            if ($cmd) { $segments.Add(@{ c = $cmd; n = $nums.ToArray() }) }
            $cmd = $t.Groups[1].Value
            $nums.Clear()
        } else {
            $nums.Add([float]$t.Groups[2].Value)
        }
    }
    if ($cmd) { $segments.Add(@{ c = $cmd; n = $nums.ToArray() }) }

    # Pass 2: build the path. Everything stays in this scope, so the current point
    # actually carries between segments.
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $cx = 0.0; $cy = 0.0      # current point, in source units
    $sx = 0.0; $sy = 0.0      # sub-path start, for Z

    foreach ($seg in $segments) {
        $n = $seg.n
        switch -CaseSensitive ($seg.c) {
            'M' {
                $cx = $n[0]; $cy = $n[1]; $sx = $cx; $sy = $cy
                for ($i = 2; $i + 1 -lt $n.Count; $i += 2) {   # implicit lineto
                    $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($n[$i]*$scale+$tx), ($n[$i+1]*$scale+$ty))
                    $cx = $n[$i]; $cy = $n[$i+1]
                }
            }
            'm' {
                $cx += $n[0]; $cy += $n[1]; $sx = $cx; $sy = $cy
            }
            'L' { for ($i = 0; $i + 1 -lt $n.Count; $i += 2) {
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($n[$i]*$scale+$tx), ($n[$i+1]*$scale+$ty))
                      $cx = $n[$i]; $cy = $n[$i+1] } }
            'l' { for ($i = 0; $i + 1 -lt $n.Count; $i += 2) {
                      $ex = $cx + $n[$i]; $ey = $cy + $n[$i+1]
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($ex*$scale+$tx), ($ey*$scale+$ty))
                      $cx = $ex; $cy = $ey } }
            'H' { foreach ($v in $n) {
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($v*$scale+$tx), ($cy*$scale+$ty))
                      $cx = $v } }
            'h' { foreach ($v in $n) {
                      $ex = $cx + $v
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($ex*$scale+$tx), ($cy*$scale+$ty))
                      $cx = $ex } }
            'V' { foreach ($v in $n) {
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($cx*$scale+$tx), ($v*$scale+$ty))
                      $cy = $v } }
            'v' { foreach ($v in $n) {
                      $ey = $cy + $v
                      $path.AddLine(($cx*$scale+$tx), ($cy*$scale+$ty), ($cx*$scale+$tx), ($ey*$scale+$ty))
                      $cy = $ey } }
            'C' { for ($i = 0; $i + 5 -lt $n.Count; $i += 6) {
                      $path.AddBezier(($cx*$scale+$tx), ($cy*$scale+$ty),
                                      ($n[$i]*$scale+$tx),   ($n[$i+1]*$scale+$ty),
                                      ($n[$i+2]*$scale+$tx), ($n[$i+3]*$scale+$ty),
                                      ($n[$i+4]*$scale+$tx), ($n[$i+5]*$scale+$ty))
                      $cx = $n[$i+4]; $cy = $n[$i+5] } }
            'c' { for ($i = 0; $i + 5 -lt $n.Count; $i += 6) {
                      $x1 = $cx + $n[$i];   $y1 = $cy + $n[$i+1]
                      $x2 = $cx + $n[$i+2]; $y2 = $cy + $n[$i+3]
                      $ex = $cx + $n[$i+4]; $ey = $cy + $n[$i+5]
                      $path.AddBezier(($cx*$scale+$tx), ($cy*$scale+$ty),
                                      ($x1*$scale+$tx), ($y1*$scale+$ty),
                                      ($x2*$scale+$tx), ($y2*$scale+$ty),
                                      ($ex*$scale+$tx), ($ey*$scale+$ty))
                      $cx = $ex; $cy = $ey } }
            'Z' { $path.CloseFigure(); $cx = $sx; $cy = $sy }
            'z' { $path.CloseFigure(); $cx = $sx; $cy = $sy }
        }
    }
    return $path
}

# Centres text in a box. RTL strings render correctly as long as the font has the glyphs.
function Draw-Centered($g, [string]$text, $font, $brush, [float]$cx, [float]$cy) {
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment     = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString($text, $font, $brush, $cx, $cy, $fmt)
    $fmt.Dispose()
}

# --------------------------------------------------------------- feature graphic

$W = 1024; $H = 500
$c = New-Canvas $W $H
$bmp = $c[0]; $g = $c[1]

# Diagonal indigo-to-ink wash.
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point 0, 0),
    (New-Object System.Drawing.Point $W, $H),
    [System.Drawing.Color]::FromArgb(58, 47, 184), $Ink)
$g.FillRectangle($grad, 0, 0, $W, $H)
$grad.Dispose()

# Soft glow behind the keys.
$glow = New-Object System.Drawing.Drawing2D.GraphicsPath
$glow.AddEllipse(-120, 40, 700, 460)
$pg = New-Object System.Drawing.Drawing2D.PathGradientBrush($glow)
$pg.CenterColor = [System.Drawing.Color]::FromArgb(70, 124, 108, 255)
$pg.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 124, 108, 255))
$g.FillPath($pg, $glow)
$pg.Dispose(); $glow.Dispose()

# Three keys spelling 343 -> the digits that find אמא.
$keys = @(
    @{ d = "3"; l = "אבג" },
    @{ d = "4"; l = "מםנן" },
    @{ d = "3"; l = "אבג" }
)
$keySize = 122.0
$gap     = 22.0
$startX  = 74.0
$keyY    = 150.0

$fDigit   = New-Object System.Drawing.Font("Segoe UI", 46, [System.Drawing.FontStyle]::Regular)
$fLetters = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Regular)
$bWhite   = New-Object System.Drawing.SolidBrush $White
$bMuted   = New-Object System.Drawing.SolidBrush $Muted

for ($i = 0; $i -lt $keys.Count; $i++) {
    $x = $startX + $i * ($keySize + $gap)
    $path = New-RoundedPath $x $keyY $keySize $keySize 26
    $fill = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(38, 255, 255, 255))
    $g.FillPath($fill, $path)
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(60, 255, 255, 255)), 1.5
    $g.DrawPath($pen, $path)
    $fill.Dispose(); $pen.Dispose(); $path.Dispose()

    Draw-Centered $g $keys[$i].d $fDigit $bWhite ($x + $keySize / 2) ($keyY + 48)
    Draw-Centered $g $keys[$i].l $fLetters $bMuted ($x + $keySize / 2) ($keyY + 95)
}

# Arrow.
$fArrow = New-Object System.Drawing.Font("Segoe UI", 38, [System.Drawing.FontStyle]::Regular)
$bArrow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(150, 255, 255, 255))
Draw-Centered $g ([char]0x2192) $fArrow $bArrow 566 ($keyY + 61)

# The payoff.
$fResult = New-Object System.Drawing.Font("Segoe UI", 72, [System.Drawing.FontStyle]::Bold)
Draw-Centered $g "אמא" $fResult $bWhite 760 ($keyY + 58)

# Wordmark and tagline.
$fName = New-Object System.Drawing.Font("Segoe UI Semibold", 33, [System.Drawing.FontStyle]::Bold)
$fTag  = New-Object System.Drawing.Font("Segoe UI", 17, [System.Drawing.FontStyle]::Regular)
Draw-Centered $g "T9 Still Alive Dialer" $fName $bWhite ($W / 2) 375
Draw-Centered $g "Real T9 contact search for every alphabet" $fTag $bMuted ($W / 2) 421

$bmp.Save("$Out\feature-graphic.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "OK -> $Out\feature-graphic.png  (1024x500)"

# ------------------------------------------------------------------------ icon

$S = 512
$c = New-Canvas $S $S
$bmp = $c[0]; $g = $c[1]

$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point 0, 0),
    (New-Object System.Drawing.Point $S, $S), $IndigoHi, $Indigo)
$g.FillRectangle($grad, 0, 0, $S, $S)
$grad.Dispose()

# The W-are-theim mark: two arms of a W with a handset standing in for the middle peak.
$penW = New-Object System.Drawing.Pen $White, 42
$penW.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$penW.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$penW.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
# The array must be typed, or PowerShell hands DrawLines an Object[] and it fails
# to pick an overload.
$leftArm = [System.Drawing.PointF[]]@(
    (New-Object System.Drawing.PointF 104, 142),
    (New-Object System.Drawing.PointF 166, 374),
    (New-Object System.Drawing.PointF 218, 256))
$rightArm = [System.Drawing.PointF[]]@(
    (New-Object System.Drawing.PointF 294, 256),
    (New-Object System.Drawing.PointF 346, 374),
    (New-Object System.Drawing.PointF 408, 142))
$g.DrawLines($penW, $leftArm)
$g.DrawLines($penW, $rightArm)
$penW.Dispose()

# The handset standing in for the W's middle peak. Same path data as
# res/drawable/ic_logo.xml, under the same transform: the vector's 108-unit viewport
# maps to 512 px, and the handset group inside it is scaled 0.95 at (42.6, 26.6).
$vec = 512.0 / 108.0
$handsetData = 'M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56 -0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 -5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02 -0.37,-1.11 -0.56,-2.3 -0.56,-3.53 0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99 3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z'
$handset = Convert-SvgPath $handsetData (0.95 * $vec) (42.6 * $vec) (26.6 * $vec)
$g.FillPath($bWhite, $handset)
$handset.Dispose()

$bmp.Save("$Out\icon-512.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "OK -> $Out\icon-512.png  (512x512)"

foreach ($f in @("$Out\feature-graphic.png", "$Out\icon-512.png")) {
    $img = [System.Drawing.Image]::FromFile($f)
    "{0}  {1}x{2}  {3}" -f (Split-Path $f -Leaf), $img.Width, $img.Height, $img.PixelFormat
    $img.Dispose()
}
