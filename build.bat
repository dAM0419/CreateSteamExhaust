@echo off
chcp 65001 >NUL
cd /d "%~dp0"
echo [Create: Steam Exhaust] 빌드를 시작합니다...
call gradlew.bat build
if %ERRORLEVEL% neq 0 (
    echo.
    echo 빌드 실패. 위의 오류 메시지를 확인하세요.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo 빌드 완료: build\libs\ 폴더의 steamexhaust-버전.jar 를 mods 폴더에 넣으세요.
pause
