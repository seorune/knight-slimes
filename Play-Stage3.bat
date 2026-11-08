@echo off
setlocal
cd /d "%~dp0"
if exist ".tools\project-env.bat" (
  call ".tools\project-env.bat"
  if errorlevel 1 goto failed
) else (
  set "GRADLE_USER_HOME=%~dp0.tools\gradle-home"
)
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Dgame.startStage=3"
call gradlew.bat :lwjgl3:run --console=plain
if errorlevel 1 goto failed
exit /b 0
:failed
echo.
echo Could not start Stage 3. Please keep this error window open.
pause
exit /b 1
