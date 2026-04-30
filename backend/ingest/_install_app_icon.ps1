# Replace Speakeater Android launcher icon with s_pour_martini.png across all
# densities + adaptive icon + Play Store 512.
Add-Type -AssemblyName System.Drawing

$src = "C:\Users\12566\Downloads\pantrie-build (1)\pantrie-build\image_assets\brimm\brand_kit\s_pour_martini.png"
$resRoot = "C:\Users\12566\Downloads\pantrie-build (1)\pantrie-build\android\app\src\main\res"

if (-not (Test-Path $src)) {
  Write-Error "Source PNG not found: $src"
  exit 1
}

$srcImg = [System.Drawing.Image]::FromFile($src)
Write-Host "Source loaded: $($srcImg.Width) x $($srcImg.Height)"

function Save-Resized($img, $size, $outPath, $padToCharcoal = $false, $contentScale = 1.0) {
  $bmp = New-Object System.Drawing.Bitmap($size, $size)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  if ($padToCharcoal) {
    $g.Clear([System.Drawing.Color]::FromArgb(13, 13, 14))  # #0D0D0E
    $contentSize = [int]($size * $contentScale)
    $offset = [int](($size - $contentSize) / 2)
    $g.DrawImage($img, $offset, $offset, $contentSize, $contentSize)
  } else {
    $g.DrawImage($img, 0, 0, $size, $size)
  }
  $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose()
  $bmp.Dispose()
}

# Legacy mipmap densities — full image, no padding
$densities = @{
  "mdpi"     = 48
  "hdpi"     = 72
  "xhdpi"    = 96
  "xxhdpi"   = 144
  "xxxhdpi"  = 192
}
foreach ($d in $densities.Keys) {
  $size = $densities[$d]
  $square = "$resRoot\mipmap-$d\ic_launcher.png"
  $round  = "$resRoot\mipmap-$d\ic_launcher_round.png"
  Save-Resized $srcImg $size $square
  Save-Resized $srcImg $size $round
  Write-Host "OK mipmap-$d ($size x $size)"
}

# Adaptive icon foreground: 432x432 with charcoal padding so the S+martini sits
# inside the 264x264 safe zone with breathing room. Content scaled to 0.74 = ~320px.
Save-Resized $srcImg 432 "$resRoot\drawable\ic_launcher_foreground.png" $true 0.74
Write-Host "OK adaptive foreground (432 x 432, content centered ~320px)"

# Play Store 512x512 — full bleed
Save-Resized $srcImg 512 "C:\Users\12566\Downloads\speakeater-play-icon-512.png"
Write-Host "OK Play Store icon: C:\Users\12566\Downloads\speakeater-play-icon-512.png (512 x 512)"

$srcImg.Dispose()
Write-Host ""
Write-Host "Done. Next APK build will use the new launcher icon."
