#!/bin/sh

# This script produces blockchain configurations which
# include Rell source code

set -eu

rm -rf target
mkdir target

# Deploying dapp
bash ./scripts/multigen.sh -d config/$DAPP_NAME -o target config/$DAPP_NAME/manifest.xml

