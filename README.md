# 2025-02-erfa-tls-handson

Chain of certificate authority HandsOn

## Docker

This repository requires `docker` to be installed to launch the required tooling. In order to build
the image, run:

```sh
docker build -t shell:latest .
```

In order to run the shell, use:

```sh
docker run --rm -e PS1 -v .:/app/host:rw -it shell:latest
```

Your current working directory will be available under `/app/host` in the Docker container, so that
you can edit files in the repository and have access to them inside the container.

## Building

In order to build the application, spawn a shell via Docker:

```sh
docker run --rm -e PS1 -v .:/app/host:rw -it shell:latest
```

Then go to the shared directory and compile the project:

```sh
cd host
mvn clean compile
```

## TODO

// say what this handson is about.

// explain the steps

// explain where to start

