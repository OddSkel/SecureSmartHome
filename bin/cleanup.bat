@echo off
REM cleanup.bat

REM Change to the parent of the folder where this .bat lives
cd /d "%~dp0.."

echo Cleaning project...

for /r %%f in (*.class) do del "%%f"
for /r %%f in (*.txt) do del "%%f"
for /r %%f in (*.csv) do del "%%f"

if exist "homes" (
    rd /s /q "homes"
)

echo Done!