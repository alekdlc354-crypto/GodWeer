@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

rem ===================================================================
rem  GodWeer / Phone - запуск Minecraft с нашим клиентом
rem
rem  ЛИЦЕНЗИЯ MINECRAFT НЕ НУЖНА.
rem  Gradle (Fabric Loom) поднимает игру в dev-режиме: Fabric Loader сам
rem  подставляет фиктивный accessToken, а клиент стартует с флагом
rem  --offlineDeveloperMode - офлайн-сессия, без обращений к Mojang,
rem  без профиля и без ключей подписи чата. Мультиплеер не отключён:
rem  играются одиночка, LAN и offline-mode серверы.
rem
rem  Использование:
rem     run client.bat                  ник из gradle.properties
rem     run client.bat Steve            свой ник
rem     run client.bat Steve 4G         ник + heap для игры
rem
rem  Переменные окружения, если аргументы не переданы:
rem     GODWEER_USERNAME=Steve   GODWEER_XMX=4G
rem
rem  Первый запуск долгий: Loom качает Minecraft 26.3, ассеты
rem  и распаковывает нативы. Дальше - секунды.
rem
rem  Мир и настройки клиента лежат в папке run\ рядом с проектом.
rem ===================================================================

cd /d %~dp0

rem ---------- ник: аргумент, окружение, значение по умолчанию ----------
set "USERNAME=%~1"
if "%USERNAME%"=="" set "USERNAME=%GODWEER_USERNAME%"
if "%USERNAME%"=="" set "USERNAME=GodWeer"

rem ---------- heap ----------
set "XMX=%~2"
if "%XMX%"=="" set "XMX=%GODWEER_XMX%"
if "%XMX%"=="" set "XMX=2G"

rem ---------- проверка ника: 3..16 символов, без пробелов ----------
if not "%USERNAME%"=="%USERNAME: =%" (
    echo [ОШИБКА] В нике не должно быть пробелов: "%USERNAME%"
    goto :fail
)
if not "!USERNAME:~17,1!"=="" (
    echo [ОШИБКА] Ник не длиннее 16 символов: "%USERNAME%"
    goto :fail
)
if "!USERNAME:~3,1!"=="" (
    echo [ОШИБКА] Ник не короче 3 символов: "%USERNAME%"
    goto :fail
)

rem ---------- ищем Java (для Minecraft 26.3 нужна JDK 25) ----------
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if "%JAVA_CMD%"=="" (
    for /f "delims=" %%I in ('where java.exe 2^>nul') do if "!JAVA_CMD!"=="" set "JAVA_CMD=%%I"
)
if "%JAVA_CMD%"=="" (
    echo [ОШИБКА] Java не найдена. Для Minecraft 26.3 нужна JDK 25.
    echo           Установи JDK 25 и/или задай переменную JAVA_HOME.
    goto :fail
)

rem ---------- версия Java - только предупреждение ----------
set "JAVA_VER="
for /f "tokens=3" %%V in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i /c:"version"') do (
    if "!JAVA_VER!"=="" set "JAVA_VER=%%V"
)
set "JAVA_VER=%JAVA_VER:"=%"
set "JAVA_MAJOR=0"
if defined JAVA_VER for /f "delims=. tokens=1" %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"
if %JAVA_MAJOR% LSS 25 (
    echo [ВНИМАНИЕ] Найдена Java %JAVA_VER%, а для Minecraft 26.3 нужна JDK 25.
    echo             Gradle попробует найти подходящий toolchain сам.
    echo.
)

rem ---------- проверяем обёртку ----------
if not exist "gradlew.bat" (
    echo [ОШИБКА] Рядом с батником нет gradlew.bat.
    goto :fail
)

rem ---------- параметры в Gradle через окружение ----------
rem (так ник не надо экранировать в командной строке)
set "GODWEER_USERNAME=%USERNAME%"
set "GODWEER_XMX=%XMX%"

echo.
echo [*] Клиент:   Phone / GodWeer, dev-запуск без лицензии
echo [*] Ник:      %USERNAME%
echo [*] Память:   -Xmx%XMX%
echo [*] Java:     %JAVA_CMD%
echo [*] Папка:    %CD%\run
echo [*] Команда:  gradlew runClient --console=plain
echo.

call gradlew.bat runClient --console=plain
set "EXITCODE=%ERRORLEVEL%"

echo.
if "%EXITCODE%"=="0" (
    echo [*] Игра закрылась, код 0.
) else (
    echo [ОШИБКА] gradlew завершился с кодом %EXITCODE%.
    echo          Смотри лог выше. Частые причины: старая Java,
    echo          мало оперативной памяти или обрыв интернета
    echo          на первом запуске.
    pause
)

exit /b %EXITCODE%

:fail
echo.
pause
exit /b 1
