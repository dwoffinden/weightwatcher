{
  description = "A flake for WeightWatcher Android development";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    git-hooks.url = "github:cachix/git-hooks.nix";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      git-hooks,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "35.0.0" ];
          platformVersions = [ "35" ];
          abiVersions = [
            "x86_64"
            "arm64-v8a"
          ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis_playstore" ];
        };
        androidSdk = androidComposition.androidsdk;
        emulator-run = pkgs.writeShellScriptBin "emulator-run" ''
          set -e
          AVD_NAME="weightwatcher"
          if ! emulator -list-avds | grep -q "^$AVD_NAME$"; then
            echo "Creating AVD: $AVD_NAME"
            echo "no" | avdmanager create avd -n "$AVD_NAME" -k "system-images;android-35;google_apis_playstore;x86_64" --force
          fi
          echo "Starting emulator: $AVD_NAME"
          emulator -avd "$AVD_NAME"
        '';
        pre-commit-check = git-hooks.lib.${system}.run {
          src = ./.;
          hooks = {
            ktfmt = {
              enable = true;
              name = "ktfmt";
              entry = "${pkgs.ktfmt}/bin/ktfmt";
              files = "\\.kt$";
            };
          };
        };
        jdk = pkgs.jdk25;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            androidSdk
            jdk
            (pkgs.gradle.override { java = jdk; })
            emulator-run
            pkgs.ktfmt
            pkgs.kotlin-language-server
          ];
          shellHook = ''
            ${pre-commit-check.shellHook}
            export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
            export JAVA_HOME="${jdk}/lib/openjdk"
          '';
        };
      }
    );
}
