@echo off
:parse
IF "%~1"=="docker" GOTO docker
IF "%~1"=="intelij" GOTO IntelliJ
IF "%~1"=="coccoc" GOTO CocCoc
:Docker
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
cls
exit
:IntelliJ
    start "" "C:\Program Files\JetBrains\IntelliJ IDEA 2022.3.1\bin\idea64.exe"
cls
exit
:CocCoc
    start "" "C:\Program Files\CocCoc\Browser\Application\browser.exe" --profile-directory="Default"
cls
exit