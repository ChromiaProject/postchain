#!/bin/sh

set -eu

export IMAGE=chromaway/postchain-subnode-nodb
export VER=3.4.0

export TARGET_DIR=target
export LOG_DIR=/opt/chromaway/postchain/$TARGET_DIR/logs
