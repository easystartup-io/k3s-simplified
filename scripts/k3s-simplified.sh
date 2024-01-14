#!/bin/bash

JAVA_DIRECTORY=/usr/local/lib/k3s-simplified-java
JAR_FILE=/usr/local/share/k3s-simplified.jar

# For macos its inside Contents/Home
if [ "$OS" = "Darwin" ]; then
    JAVA_DIRECTORY="${JAVA_DIRECTORY}/jdk-21.0.1.jdk/Contents/Home";
fi
# Run the JAR file with the newly downloaded or existing Java
$JAVA_DIRECTORY/bin/java -jar "${JAR_FILE}" "$@"