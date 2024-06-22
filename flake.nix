{
  description = "scala development shell";
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
    ...
  }: let
    forSystem = system: let
      pkgs = nixpkgs.legacyPackages.${system};
      jdk = pkgs.jdk17_headless;
    in {
      devShell = pkgs.mkShell {
        name = "scala-dev-shell";
        buildInputs = [
          jdk
          pkgs.coursier
          pkgs.sbt
          pkgs.bloop
          pkgs.scala_3
        ];
      };
    };
  in
    flake-utils.lib.eachDefaultSystem forSystem;
}
