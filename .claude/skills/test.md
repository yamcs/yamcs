# Test

Run YAMCS unit tests using Maven.

## Usage

`/test` — run all tests  
`/test <module>` — run tests for a specific Maven module (e.g. `yamcs-xtce`, `yamcs-core`)  
`/test <module> <TestClass>` — run a single test class within a module  

## Steps

Parse the args:
- If a test class is given: `mvn test -pl <module> -am -Dtest=<TestClass>`
- If only a module is given: `mvn test -pl <module> -am`
- Otherwise: `mvn test`

Run from `/Users/nikhil/GSS Workspace/yamcs-Pixxel-fork`.

Report: pass/fail counts, any test failures with their error messages.