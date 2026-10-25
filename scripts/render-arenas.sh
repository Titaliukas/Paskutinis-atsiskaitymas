#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Run ./mvnw -B -ntp clean verify first, including compilation of the QA utilities.
output="$PWD/game-client/target/arena-previews"
java -Djava.awt.headless=true -cp game-server/target/test-classes:game-server/target/game-server.jar \
  lt.ktu.paskutinisatsiskaitymas.server.ArenaPreviewData "$output"
java -Djava.awt.headless=true -cp game-client/target/test-classes:game-client/target/game-client.jar \
  lt.ktu.paskutinisatsiskaitymas.client.ArenaPreviewRenderer "$output"
