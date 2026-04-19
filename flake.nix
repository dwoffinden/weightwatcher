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
        buildToolsVersion = "36.0.0";
        platformVersion = "36";
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ buildToolsVersion ];
          platformVersions = [ platformVersion ];
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
          TARGET_IMAGE="system-images;android-${platformVersion};google_apis_playstore;x86_64"

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
            ktfmt = {
              enable = true;
              name = "ktfmt";
              entry = "${pkgs.ktfmt}/bin/ktfmt";
              files = "\\.kt$";
            };
          };
        };
        jdk = pkgs.jdk25_headless;
        # gradle.properties is generated into the Nix store and symlinked at
        # shell entry so Gradle sees the correct SDK and JDK store paths.
        gradleProperties = pkgs.writeText "gradle.properties" ''
          android.useAndroidX=true
          android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2
          org.gradle.java.home=${jdk}/lib/openjdk
        '';
        ciGradleProperties = pkgs.writeText "gradle.properties" ''
          android.useAndroidX=true
          android.aapt2FromMavenOverride=${ciAndroidSdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2
          org.gradle.java.home=${jdk}/lib/openjdk
        '';
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            androidSdk
            jdk
            (pkgs.gradle_9.override { java = jdk; })
            emulator-run
            pkgs.ktfmt
            pkgs.kotlin-language-server
          ];
          shellHook = ''
            ${pre-commit-check.shellHook}
            export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
            export JAVA_HOME="${jdk}/lib/openjdk"
            ln -sf ${gradleProperties} gradle.properties
          '';
        };
        # Minimal shell for CI: skips emulator, system images, and dev tooling.
        devShells.ci = pkgs.mkShell {
          packages = [
            ciAndroidSdk
            jdk
            (pkgs.gradle_9.override { java = jdk; })
          ];
          shellHook = ''
            export ANDROID_HOME="${ciAndroidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="${ciAndroidSdk}/libexec/android-sdk"
            export JAVA_HOME="${jdk}/lib/openjdk"
            ln -sf ${ciGradleProperties} gradle.properties
          '';
        };
      }
    );
}
