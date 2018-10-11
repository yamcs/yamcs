@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set BASE=%~dp0..
echo %BASE%
set YAMCS_HOME=%BASE%

set LOCALCLASSPATH=
for %%i in ("%BASE%\lib\*.jar") do call "%BASE%\bin\lcp.bat" %%i
set LOCALCLASSPATH=%LOCALCLASSPATH%;%BASE%\etc


set JAVA=java.exe
set JAVA_ARGS=-client -classpath "%LOCALCLASSPATH%" -Xmx300M
%JAVA% %JAVA_ARGS% org.yamcs.YamcsServer

if "%OS%"=="Windows_NT" @endlocal
