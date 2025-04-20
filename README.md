# TLS HandsOn

> This repository was created for a hands-on session.

## TL;DR

This repository is supporting material to learn more about certificates, `openssl`, and how chains
of trust work.

Before starting with the hands-on, we recommend preforming the docker build step below, as it can
take a couple of minutes.

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

In order to build the Java application, spawn a shell via Docker:

```sh
docker run --name shell -e PS1 -p 8443:8443 -v .:/app/host:rw -it shell:latest
```

If the Docker container already exists (see `docker ps -a`), you can start and attach it:

```bash
docker start shell
docker attach shell
```

Then go to the shared directory and compile the project:

```sh
cd host
mvn clean compile
```

## Material

This repository contains two markdown files which serve as tutorials:

- [`01_create_chain_of_trust.md`](./01_create_chain_of_trust.md)
- [`02_verify_chain_of_trust.md`](./02_verify_chain_of_trust.md)

Simply navigate to these files and follow the tutorials. Make sure you understand why you perform
each step.