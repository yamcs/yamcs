@echo off
setlocal

if "%1" == "" (
    echo usage: %0 EXAMPLE [options]
    echo.
    echo Where EXAMPLE is one of:
    call :printExampleList
    exit /B 1
)

if not exist examples\%1 (
    echo Cannot find an example by the name '%1'. Use one of:
    call :printExampleList
    exit /B 1
)

rem Enable ansi color processing
set ENABLE_VIRTUAL_TERMINAL_PROCESSING=1

set example=%1
set args=
shift
:readarg
if not "%1" == "" (
    set args=%args% %1
    shift
    goto readarg
)

mvn -f examples\%example%\pom.xml yamcs:run -Dyamcs.args="%YAMCS_OPTS% %args%"
exit /B 0


:printExampleList
for /f %%i in ( 'dir /a:d /b examples ^| find /V "snippets" ^| findstr /V "\..*"' ) do (
    echo.    %%i
)
