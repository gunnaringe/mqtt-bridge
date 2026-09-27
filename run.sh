#!/usr/bin/env bash
# Build and run locally. Toolchain (Java, Maven) comes from mise.toml.
set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR"

mise exec -- mvn -B package
mise exec -- java -jar target/wg2mqtt-1.0-SNAPSHOT.jar "${@:-config.yaml}"
