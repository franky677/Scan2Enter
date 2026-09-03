$ErrorActionPreference = "Stop"

$projectRoot = Get-Location
$drawable = Join-Path $projectRoot "app\src\main\res\drawable"

if (-not (Test-Path $drawable)) {
    throw "Cartella drawable non trovata: $drawable`nEsegui questo script dalla cartella principale di Scan2Enter2."
}

$coins = @(
    @{
        File = "euro_coin_2euro.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_2euro_800.jpg"
    },
    @{
        File = "euro_coin_1euro.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_1euro_800.jpg"
    },
    @{
        File = "euro_coin_50cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_50cent_800.jpg"
    },
    @{
        File = "euro_coin_20cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_20cent_800.jpg"
    },
    @{
        File = "euro_coin_10cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/10common.jpg"
    },
    @{
        File = "euro_coin_5cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_5cent_800.jpg"
    },
    @{
        File = "euro_coin_2cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_2cent_800.jpg"
    },
    @{
        File = "euro_coin_1cent.jpg"
        Url  = "https://www.ecb.europa.eu/euro/coins/common/shared/img/common_1cent_800.jpg"
    }
)

Write-Host ""
Write-Host "Download monete euro ufficiali BCE..." -ForegroundColor Cyan

foreach ($coin in $coins) {
    $dest = Join-Path $drawable $coin.File
    Write-Host ("  " + $coin.File)
    Invoke-WebRequest -Uri $coin.Url -OutFile $dest
}

Write-Host ""
Write-Host "Completato. File salvati in:" -ForegroundColor Green
Write-Host $drawable
Write-Host ""
Get-ChildItem $drawable -Filter "euro_coin_*.jpg" |
    Select-Object Name, Length |
    Format-Table -AutoSize
