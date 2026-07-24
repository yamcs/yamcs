# Build

Build the YAMCS project using Maven.

## Usage

`/build` — full project, skip tests  
`/build <module>` — build a specific Maven module (e.g. `yamcs-xtce`, `yamcs-web`, `yamcs-core`)  
`/build --tests` — full build including tests  

## Steps

Parse the args:
- If `--tests` is present, run `mvn install` (with tests)
- If a module name is given, run `mvn install -DskipTests -pl <module> -am`
- Otherwise, run `mvn install -DskipTests` from the project root

Run from `/Users/nikhil/GSS Workspace/yamcs-Pixxel-fork`.

Report: whether the build succeeded, any compilation errors, and how long it took.