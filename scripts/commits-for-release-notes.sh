#!/bin/bash

set -e

ROOT_DIR=$(dirname $(readlink -f $0))/..

LAST_VERSION=$1

REPLACEMENT="perl -pe s|(.*?)(\(?#(\d+)\)?(\s\(#\d+\))?)?$|\*\1\[#\3\]\(https://github.com/apache/incubator-pekko-http/pull/\3\)|"

echo "#### pekko-http-core"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http-core | $REPLACEMENT

echo
echo "#### pekko-http"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http | $REPLACEMENT

echo
echo "#### pekko-http-marshallers"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http-marshallers* | $REPLACEMENT

echo
echo "#### pekko-http-testkit"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http-testkit | $REPLACEMENT

echo
echo "#### docs"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/docs | $REPLACEMENT

echo
echo "#### pekko-http2-support"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http2-support | $REPLACEMENT

echo
echo "#### pekko-http-caching"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/http-caching | $REPLACEMENT

echo
echo "#### build"
echo
git log --no-merges --reverse --oneline ${LAST_VERSION}.. -- $ROOT_DIR/project $ROOT_DIR/*.sbt | $REPLACEMENT | grep -v Update

