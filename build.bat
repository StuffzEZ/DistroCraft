@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "SELF=%~dp0"
cd /d "%SELF%"
set "OUTDIR=%SELF%build-output"

:menu
cls
echo ====================================
echo    DistroCraft Build Menu
echo ====================================
echo.
echo  1. Build everything
echo.
echo  2. Server Mod (Fabric)
echo  3. Server Mod (NeoForge)
echo  4. Server Mod (both)
echo.
echo  5. Player Mod (Fabric)
echo  6. Player Mod (NeoForge)
echo  7. Player Mod (both)
echo.
echo  8. Player App (standalone)
echo  9. Server Plugin (Paper)
echo.
echo  A. Export last build JARs to /build-output
echo  C. Clean all build artifacts
echo  Q. Quit
echo.
set /p choice="Select option: "

if "%choice%"=="1"  goto :all
if "%choice%"=="2"  set "target=:server-mod:fabric:build"      & set "proj=server-mod-fabric"      & call :doBuild & goto :menu
if "%choice%"=="3"  set "target=:server-mod:neoforge:build"    & set "proj=server-mod-neoforge"    & call :doBuild & goto :menu
if "%choice%"=="4"  set "target=:server-mod:fabric:build :server-mod:neoforge:build" & call :doBuild & goto :menu
if "%choice%"=="5"  set "target=:player-mod:fabric:build"      & set "proj=player-mod-fabric"      & call :doBuild & goto :menu
if "%choice%"=="6"  set "target=:player-mod:neoforge:build"    & set "proj=player-mod-neoforge"    & call :doBuild & goto :menu
if "%choice%"=="7"  set "target=:player-mod:fabric:build :player-mod:neoforge:build" & call :doBuild & goto :menu
if "%choice%"=="8"  set "target=:player-app:build"             & set "proj=player-app"             & call :doBuild & goto :menu
if "%choice%"=="9"  set "target=:server-plugin:build"          & set "proj=server-plugin"          & call :doBuild & goto :menu
if /i "%choice%"=="Q" exit /b
if /i "%choice%"=="A" goto :export
if /i "%choice%"=="C" goto :clean

echo Invalid choice. & timeout /t 2 >nul & goto :menu

:doBuild
cls
echo ====================================
echo    Building...
echo ====================================
echo.
call .\gradlew %target% --no-daemon %*
echo.
echo ====================================
if errorlevel 1 (echo    BUILD FAILED) else (echo    BUILD SUCCESSFUL)
echo ====================================
if not errorlevel 1 call :showJars
echo.
echo Press any key to continue...
pause >nul
exit /b

:clean
cls
echo ====================================
echo    Cleaning...
echo ====================================
echo.
call .\gradlew :server-mod:common:clean :server-mod:fabric:clean :server-mod:neoforge:clean :player-mod:common:clean :player-mod:fabric:clean :player-mod:neoforge:clean :player-app:clean :server-plugin:clean --no-daemon
if errorlevel 1 (
    echo.
    echo Gradle clean failed ^(likely locked files^). Trying direct removal...
    echo.
    powershell -NoProfile -Command ^
        $dirs = @( ^
            'server-mod\common\build', 'server-mod\fabric\build', 'server-mod\neoforge\build', ^
            'player-mod\common\build', 'player-mod\fabric\build', 'player-mod\neoforge\build', ^
            'player-app\build', 'server-plugin\build' ^
        ); ^
        $root = '%~dp0'.TrimEnd('\'); ^
        $ok = $true; ^
        foreach ($d in $dirs) { ^
            $p = Join-Path $root $d; ^
            if (Test-Path $p) { ^
                Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue; ^
                if (Test-Path $p) { ^
                    Write-Host "  FAILED: $d"; $ok = $false; ^
                } else { ^
                    Write-Host "  Removed $d"; ^
                } ^
            } ^
        }; ^
        if (-not $ok) { exit 1 }
)
echo.
echo Done.
echo.
echo Press any key to return to menu...
pause >nul
goto :menu

:export
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
echo.
echo Copying JARs to %OUTDIR%...
echo.
set "copied=0"
for %%p in (
    "server-mod\fabric\build\libs\*.jar"
    "server-mod\neoforge\build\libs\*.jar"
    "player-mod\fabric\build\libs\*.jar"
    "player-mod\neoforge\build\libs\*.jar"
    "player-app\build\libs\*.jar"
    "server-plugin\build\libs\*.jar"
) do (
    for %%f in ("%%~p") do (
        if exist "%%f" (
            copy "%%f" "%OUTDIR%" >nul
            echo   %%f
            set /a copied+=1
        )
    )
)
echo.
if !copied! equ 0 (
    echo   (none found - build something first)
) else (
    echo Done - !copied! file^(s^) copied to %OUTDIR%
)
echo.
echo Press any key to return to menu...
pause >nul
goto :menu

:showJars
echo.
echo JARs created:
echo.
if "%proj%"=="server-mod-fabric"   call :list server-mod fabric
if "%proj%"=="server-mod-neoforge" call :list server-mod neoforge
if "%proj%"=="player-mod-fabric"   call :list player-mod fabric
if "%proj%"=="player-mod-neoforge" call :list player-mod neoforge
if "%proj%"=="player-app"          call :list player-app
if "%proj%"=="server-plugin"       call :list server-plugin
if "%target%"==":server-mod:fabric:build :server-mod:neoforge:build" (
    call :list server-mod fabric & call :list server-mod neoforge
)
if "%target%"==":player-mod:fabric:build :player-mod:neoforge:build" (
    call :list player-mod fabric & call :list player-mod neoforge
)
if "%proj%"=="" (
    call :list server-mod fabric    & call :list server-mod neoforge
    call :list player-mod fabric    & call :list player-mod neoforge
    call :list player-app           & call :list server-plugin
)
exit /b

:list
if "%~2"=="" (
    for %%f in ("%~1\build\libs\*.jar") do echo   %%f
) else (
    for %%f in ("%~1\%~2\build\libs\*.jar") do echo   %%f
)
exit /b

:all
set "target=:server-mod:common:build :server-mod:fabric:build :server-mod:neoforge:build :player-mod:common:build :player-mod:fabric:build :player-mod:neoforge:build :player-app:build :server-plugin:build"
set "proj="
call :doBuild
goto :menu
