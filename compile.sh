#!/bin/bash
# compile.sh

rm -rf bin
mkdir bin

CP="src/main/java"
for jar in lib/*.jar; do CP="$CP:$jar"; done

javac -d bin -cp "$CP" \
  src/main/java/com/autogradingsystem/PathConfig.java \
  src/main/java/com/autogradingsystem/model/*.java \
  src/main/java/com/autogradingsystem/extraction/model/*.java \
  src/main/java/com/autogradingsystem/discovery/model/*.java \
  src/main/java/com/autogradingsystem/extraction/service/*.java \
  src/main/java/com/autogradingsystem/discovery/service/*.java \
  src/main/java/com/autogradingsystem/execution/service/*.java \
  src/main/java/com/autogradingsystem/analysis/service/*.java \
  src/main/java/com/autogradingsystem/penalty/model/*.java \
  src/main/java/com/autogradingsystem/penalty/strategies/*.java \
  src/main/java/com/autogradingsystem/penalty/service/*.java \
  src/main/java/com/autogradingsystem/extraction/controller/*.java \
  src/main/java/com/autogradingsystem/discovery/controller/*.java \
  src/main/java/com/autogradingsystem/execution/controller/*.java \
  src/main/java/com/autogradingsystem/analysis/controller/*.java \
  src/main/java/com/autogradingsystem/penalty/controller/*.java \
  src/main/java/com/autogradingsystem/Main.java

echo "Compilation complete!"