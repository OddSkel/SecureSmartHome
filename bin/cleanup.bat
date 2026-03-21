@echo off
REM cleanup.bat

echo Cleaning project...

REM Remove all .class files recursively
for /r %%f in (*.class) do del "%%f"

REM Remove all .txt files recursively
for /r %%f in (*.txt) do del "%%f"

REM Remove the homes folder and everything inside
if exist "homes" (
    rd /s /q "homes"
)

echo Done!