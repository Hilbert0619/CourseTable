# Makes StudyHour's launcher icon foreground (android/app/src/main/res/drawable-*dpi/ic_launcher_foreground.png) from
# the iOS icon (CourseTable/Assets.xcassets/AppIcon.appiconset/AppIcon.png) with .NET's System.Drawing (there is no
# Python imaging library on this PC). The picture takes $units of the adaptive layer's 108 (66: the artwork stays
# inside the circle mask); the rest of the layer repeats the picture's edge pixels. Also writes icon_masks.png (the
# circle and a rounded square) to $previewFolder for checking. PowerShell's execution policy blocks .ps1 files here, so run
# it from the repository folder as a script block:
#   & ([ScriptBlock]::Create((Get-Content -Raw tools\make_android_icon.ps1))) -units 66 -previewFolder $env:TEMP
param([double]$units = 66, [string]$previewFolder = $env:TEMP)
Add-Type -AssemblyName System.Drawing
$root = (Get-Location).Path
$res = Join-Path $root "android\app\src\main\res"
$scratch = $previewFolder
$src = [System.Drawing.Bitmap]::FromFile((Join-Path $root "CourseTable\Assets.xcassets\AppIcon.appiconset\AppIcon.png"))
# The picture takes $units of the adaptive layer's 108; the rest of the layer repeats the picture's edge pixels.
$n = 1024; $margin = [int][Math]::Round($n * (108 - $units) / $units / 2); $full = $n + 2 * $margin; $far = $margin + $n; $last = $n - 1
$ext = New-Object System.Drawing.Bitmap($full, $full, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($ext)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$attrs = New-Object System.Drawing.Imaging.ImageAttributes
$attrs.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
$unit = [System.Drawing.GraphicsUnit]::Pixel
function Strip($dx, $dy, $dw, $dh, $sx, $sy, $sw, $sh) { $g.DrawImage($src, (New-Object System.Drawing.Rectangle($dx, $dy, $dw, $dh)), $sx, $sy, $sw, $sh, $unit, $attrs) }
Strip 0 $margin $margin $n 0 0 1 $n
Strip $far $margin $margin $n $last 0 1 $n
Strip $margin 0 $n $margin 0 0 $n 1
Strip $margin $far $n $margin 0 $last $n 1
$corners = @((0, 0, 0, 0), ($far, 0, $last, 0), (0, $far, 0, $last), ($far, $far, $last, $last))
foreach ($corner in $corners) {
  $brush = New-Object System.Drawing.SolidBrush($src.GetPixel($corner[2], $corner[3]))
  $g.FillRectangle($brush, $corner[0], $corner[1], $margin, $margin); $brush.Dispose()
}
$g.DrawImage($src, (New-Object System.Drawing.Rectangle($margin, $margin, $n, $n)), 0, 0, $n, $n, $unit, $attrs)
$g.Dispose()
foreach ($d in @(("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432))) {
  $size = $d[1]
  $out = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $og = [System.Drawing.Graphics]::FromImage($out)
  $og.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $og.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $og.DrawImage($ext, (New-Object System.Drawing.Rectangle(0, 0, $size, $size)), 0, 0, $full, $full, $unit, $attrs)
  $og.Dispose()
  $out.Save((Join-Path (Join-Path $res ("drawable-" + $d[0])) "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $out.Dispose()
}
$preview = New-Object System.Drawing.Bitmap(432, 432)
$pg = [System.Drawing.Graphics]::FromImage($preview); $pg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$pg.DrawImage($ext, 0, 0, 432, 432); $pg.Dispose()
$ext.Dispose(); $src.Dispose()
# The circle (66 of 108) and a rounded square (72 of 108), as launchers mask the layer.
$sheet = New-Object System.Drawing.Bitmap(900, 450)
$sg = [System.Drawing.Graphics]::FromImage($sheet); $sg.Clear([System.Drawing.Color]::FromArgb(40, 44, 52)); $sg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$brush = New-Object System.Drawing.TextureBrush($preview)
$circle = New-Object System.Drawing.Drawing2D.GraphicsPath; $circle.AddEllipse(84, 84, 264, 264)
$sg.FillPath($brush, $circle)
$square = New-Object System.Drawing.Drawing2D.GraphicsPath; $r = 80; $x0 = 72; $w = 288
$square.AddArc($x0, $x0, $r, $r, 180, 90); $square.AddArc($x0 + $w - $r, $x0, $r, $r, 270, 90); $square.AddArc($x0 + $w - $r, $x0 + $w - $r, $r, $r, 0, 90); $square.AddArc($x0, $x0 + $w - $r, $r, $r, 90, 90); $square.CloseFigure()
$sg.TranslateTransform(450, 0); $brush.TranslateTransform(450, 0); $sg.FillPath($brush, $square)
$sg.Dispose(); $sheet.Save("$scratch\icon_masks.png", [System.Drawing.Imaging.ImageFormat]::Png); $sheet.Dispose(); $preview.Dispose()
"margin $margin, full $full"
