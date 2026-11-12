param(
    [string]$Source = "$PSScriptRoot/../docs/app-icon/source-full-bleed.png"
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$resourceRoot = [IO.Path]::GetFullPath("$PSScriptRoot/../app/src/main/res")
$original = [System.Drawing.Bitmap]::new([IO.Path]::GetFullPath($Source))
try {
    if ($original.Width -ne $original.Height) { throw 'App icon source must be square.' }
    # The full-bleed source has no baked white matte or pre-rounded outer frame.
    $outputs = @{ 'drawable-nodpi/app_icon.png' = 512 }
    $densities = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $densities.Keys) {
        $outputs["mipmap-$density/ic_launcher.png"] = $densities[$density]
        $outputs["mipmap-$density/ic_launcher_round.png"] = $densities[$density]
    }
    foreach ($relativePath in $outputs.Keys) {
        $size = $outputs[$relativePath]
        $destination = Join-Path $resourceRoot $relativePath
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        $bitmap = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
        $mask = [System.Drawing.Drawing2D.GraphicsPath]::new()
        try {
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
            $imageBounds = [System.Drawing.Rectangle]::new(0, 0, $size, $size)
            if ($relativePath.StartsWith('mipmap-')) {
                # Legacy PNG fallbacks use launcher masks; the shared in-app image keeps the full source.
                if ($relativePath.EndsWith('ic_launcher_round.png')) {
                    $mask.AddEllipse(0, 0, $size, $size)
                } else {
                    $diameter = [int]($size * .5)
                    $farEdge = $size - $diameter
                    $mask.AddArc(0, 0, $diameter, $diameter, 180, 90)
                    $mask.AddArc($farEdge, 0, $diameter, $diameter, 270, 90)
                    $mask.AddArc($farEdge, $farEdge, $diameter, $diameter, 0, 90)
                    $mask.AddArc(0, $farEdge, $diameter, $diameter, 90, 90)
                    $mask.CloseFigure()
                }
                $graphics.SetClip($mask)
            }
            $graphics.DrawImage($original, $imageBounds,
                0, 0, $original.Width, $original.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
            $bitmap.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "$relativePath ${size}x${size}"
        } finally {
            $mask.Dispose()
            $attributes.Dispose()
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
} finally {
    $original.Dispose()
}
