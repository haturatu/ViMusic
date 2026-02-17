#!/bin/bash
VERSIONS=$(cat gradle.properties | grep "version=" | cut -d'=' -f2)

echo "Releasing version $VERSIONS"

git add gradle.properties
git commit -m "update: Release version v$VERSIONS"

git branch --contains || exit 1

make release
git push origin master
