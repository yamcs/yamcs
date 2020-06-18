@echo off
setlocal

set YAMCS_HOME=%~dp0..

rem cd into YAMCS_HOME to support relative links in configuration files
cd %YAMCS_HOME%

rem Enable ansi color processing
set ENABLE_VIRTUAL_TERMINAL_PROCESSING=1

java -cp "lib\*;lib\ext\*" org.yamcs.YamcsServer %*
