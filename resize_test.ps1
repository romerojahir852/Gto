
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile('c:\Users\HP\OneDrive\Documentos\GitHub\MotoGP\Gto\image.png')
$w = $img.Width
$h = $img.Height

function ResizeAndSave($maxDim, $outPath, $quality) {
    $scale = $maxDim / [Math]::Max($w, $h)
    $nw = [int]($w * $scale)
    $nh = [int]($h * $scale)
    $bmp = New-Object System.Drawing.Bitmap $nw, $nh
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, $nw, $nh)
    
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $encParams = New-Object System.Drawing.Imaging.EncoderParameters 1
    $encParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, [long]$quality)
    
    $bmp.Save($outPath, $codec, $encParams)
    $bmp.Dispose()
    $g.Dispose()
}

ResizeAndSave 960 'c:\Users\HP\OneDrive\Documentos\GitHub\MotoGP\Gto\test_960.jpg' 80
ResizeAndSave 640 'c:\Users\HP\OneDrive\Documentos\GitHub\MotoGP\Gto\test_640.jpg' 75
ResizeAndSave 480 'c:\Users\HP\OneDrive\Documentos\GitHub\MotoGP\Gto\test_480.jpg' 70
$img.Dispose()
