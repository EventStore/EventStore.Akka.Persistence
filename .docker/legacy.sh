#!/usr/bin/env bash
set -e
SCRIPT_DIR=$(dirname $BASH_SOURCE)
docker-compose -p legacy -f $SCRIPT_DIR/legacy.yml "$@"