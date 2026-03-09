#!/bin/bash
# run.sh
CP="bin"
for jar in lib/*.jar; do CP="$CP:$jar"; done
java -cp "$CP" com.autogradingsystem.Main