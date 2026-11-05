{
  description = "A flake for WeightWatcher Android development";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    git-hooks.url = "github:cachix/git-hooks.nix";
    git-hooks.inputs.nixpkgs.follows = "nixpkgs";
    flint.url = "github:notashelf/flint";
    flint.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs =
    {
      self,
      nixpkgs,
      git-hooks,
      flint,
      ...
    }:
    let
      forEachSystem = nixpkgs.lib.genAttrs nixpkgs.lib.systems.flakeExposed;
    in
    {
      devShells = forEachSystem (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          buildToolsVersion = "37.0.0";
          platformVersion = "37";
          emulatorPlatformVersion = "36";
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ buildToolsVersion ];
            platformVersions = [
              platformVersion
              emulatorPlatformVersion
            ];
            abiVersions = [
              "x86_64"
              "arm64-v8a"
            ];
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis_playstore" ];
          };
          androidSdk = androidComposition.androidsdk;
          # Minimal SDK for CI: no emulator, no system images, no ABI splits.
          ciAndroidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ buildToolsVersion ];
            platformVersions = [ platformVersion ];
            includeEmulator = false;
            includeSystemImages = false;
          };
          ciAndroidSdk = ciAndroidComposition.androidsdk;
          emulator-run = pkgs.writeShellScriptBin "emulator-run" ''
            set -e
            AVD_NAME="weightwatcher"
            TARGET_IMAGE="system-images;android-${emulatorPlatformVersion};google_apis_playstore;x86_64"

            # Check if the AVD exists and is healthy
            AVD_INFO=$(avdmanager list avd)
            if echo "$AVD_INFO" | grep -q "Name: $AVD_NAME"; then
              if echo "$AVD_INFO" | grep -A 5 "Name: $AVD_NAME" | grep -q "Error:"; then
                echo "AVD $AVD_NAME is broken or missing system image. Deleting..."
                avdmanager delete avd -n "$AVD_NAME"
              fi
            fi

            if ! emulator -list-avds | grep -q "^$AVD_NAME$"; then
              echo "Creating AVD: $AVD_NAME"
              echo "no" | avdmanager create avd -n "$AVD_NAME" -k "$TARGET_IMAGE" --force
            fi
            echo "Starting emulator: $AVD_NAME"
            emulator -avd "$AVD_NAME"
          '';
          pre-commit-check = git-hooks.lib.${system}.run {
            src = ./.;
            hooks = {
              yamlfmt = {
                enable = true;
                settings.lint-only = false;
              };
              ktfmt = {
                enable = true;
                name = "ktfmt";
                entry = "${pkgs.ktfmt}/bin/ktfmt --google-style";
                files = "\\.kts?$";
              };
              xmlformat = {
                enable = true;
                name = "xmlformat";
                entry = "${pkgs.xmlformat}/bin/xmlformat -i";
                files = "\\.xml$";
              };
              flint = {
                enable = true;
                name = "flint";
                entry = "${flint.packages.${system}.default}/bin/flint --fail-if-multiple-versions";
                files = "flake\\.(nix|lock)$";
                pass_filenames = false;
              };
            };
          };
          jdk = pkgs.jdk25_headless;
          # gradle.properties is generated into the Nix store and symlinked at
          # shell entry so Gradle sees the correct SDK store path.
          mkGradleProperties =
            sdk:
            pkgs.writeText "gradle.properties" ''
              android.aapt2FromMavenOverride=${sdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2
              org.gradle.jvmargs=-Xmx4096m -XX:+HeapDumpOnOutOfMemoryError
              org.gradle.daemon=true
              org.gradle.parallel=true
              org.gradle.configureondemand=true
            '';
        in
        {
          default = pkgs.mkShell {
            packages = [
              androidSdk
              jdk
              (pkgs.gradle_9.override { java = jdk; })
              emulator-run
              pkgs.ktfmt
              pkgs.xmlformat
              flint.packages.${system}.default
              pkgs.kotlin-language-server
            ];
            shellHook = ''
              ${pre-commit-check.shellHook}
              export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
              export JAVA_HOME="${jdk}/lib/openjdk"
              ln -sf ${mkGradleProperties androidSdk} gradle.properties
            '';
          };
          # Minimal shell for CI: skips emulator, system images, and dev tooling.
          ci = pkgs.mkShell {
            packages = [
              ciAndroidSdk
              jdk
              (pkgs.gradle_9.override { java = jdk; })
            ];
            shellHook = ''
              export ANDROID_HOME="${ciAndroidSdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="${ciAndroidSdk}/libexec/android-sdk"
              export JAVA_HOME="${jdk}/lib/openjdk"
              ln -sf ${mkGradleProperties ciAndroidSdk} gradle.properties
            '';
          };
        }
      );
    };
}
