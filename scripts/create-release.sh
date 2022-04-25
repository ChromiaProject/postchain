#!/bin/sh
# Copyright (c) 2022 ChromaWay Inc. See README for license information.

set -eu

mvn clean -B gitflow:release-start \
  -DfetchRemote=false \
  -DpushRemote=false

RELEASE_BRANCH=$(git branch --show-current)
mvn clean install -DskipTests

mvn -B gitflow:release-finish \
  -DskipTestProject=true \
  -DfetchRemote=false \
  -DkeepBranch=false \
  -DversionDigitToIncrement=1 \
  -DpushRemote=false

git branch -D $RELEASE_BRANCH

mvn -B gitflow:support-start \
  -DskipTestProject=true \
  -DfetchRemote=false \
  -DpushRemote=false
