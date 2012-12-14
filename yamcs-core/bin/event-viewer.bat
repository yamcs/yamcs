@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set BASE=%~dp0..

set LOCALCLASSPATH=
for %%i in ("%BASE%\lib\*.jar") do call "%BASE%\bin\lcp.bat" %%i
for %%i in ("%BASE%\lib\ext\*.jar") do call "%BASE%\bin\lcp.bat" %%i
set LOCALCLASSPATH=%LOCALCLASSPATH%;%BASE%\etc

set JAVA=javaw.exe
set JAVA_ARGS=-client -classpath "%LOCALCLASSPATH%"
start %JAVA% %JAVA_ARGS% org.yamcs.ui.eventviewer.EventViewer

if "%OS%"=="Windows_NT" @endlocal
