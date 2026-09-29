# Containerised development build and run

Build and run Yamcs from this source checkout without installing a JDK,
Maven or npm. Works the same with docker compose and rootless
podman-compose.

    cd docker
    docker compose up

The first run builds the whole workspace inside a Maven container (the
Maven repository is cached in a named volume, so later runs are
incremental) and then starts the simulation example. Yamcs is available
on http://localhost:8090 once started.

To run another example from the examples/ directory:

    EXAMPLE=cfdp docker compose up

The port is published on 127.0.0.1 only, because the examples run
without authentication.
