# syntax = docker/dockerfile:1.2

FROM nixos/nix:2.26.2 AS builder

WORKDIR /app
COPY flake.nix .
COPY flake.lock .

RUN nix \
    --extra-experimental-features "nix-command flakes" \
    --option filter-syscalls false \
    develop -c echo

ENTRYPOINT ["bash", "-c", "nix --extra-experimental-features \"nix-command flakes\" develop"]
