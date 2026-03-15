# Reproducible builds

This process lets you verify that the version of the app that was downloaded from the Githud Releases matches the source code in our public repository.

This is achieved by replicating the build environment as Docker images.

Currently, only the following binaries are reproducible:

- [x] JAR package (`signal-cli-XXX.tar.gz`)
- [ ] Native binary (`signal-cli-XXX-Linux-native.tar.gz`)
- [x] Rust client binary (`signal-cli-XXX-Linux-client.tar.gz`)

In the following section, we will use Signal version 0.14.0 as the reference example. Simply replace all occurrences of 0.14.0 with the version number you are about to verify.

## Step-by-step instructions

### 0. Prerequisites

Before you begin, ensure you have the following installed:

- git
- docker (or podman)

### 1. Verifying reproducibility

```bash
git clone --depth 1 --branch v0.14.0 https://github.com/AsamK/signal-cli
cd ./signal-cli
./reproducible-builds/verify.sh
```

If each one ends with `... matches!` for every binary (except the native one for now), you're good to go! You've successfully verified that the Github Release binaries were built from exactly the same code as is in the signal-cli git repository.

If you get `... doesn't match!`, it means something went wrong (except for the native one for now). Please [open an issue](https://github.com/AsamK/signal-cli/issues/new/choose).
