{
  description = "OpenSSL Shell";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/release-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
  }:
    flake-utils.lib.eachDefaultSystem (
      system: let
        pkgs = import nixpkgs {inherit system;};
      in
        with pkgs; {
          devShell = mkShell {
            name = "Pinned OpenSSL Shell";
            nativeBuildInputs = [
              # Add packages here that you want to install and have pinned.
              # In order to pin them, the flake.lock file needs to be generated, using `nix develop`
              # outside the docker container.
              openssl
              jdk21_headless
              maven
            ];
          };
        }
    );
}
