#!/usr/bin/env bash
set -e
SCRIPT_DIR=$(dirname $BASH_SOURCE)
docker-compose -p esdb -f $SCRIPT_DIR/esdb.yml "$@"