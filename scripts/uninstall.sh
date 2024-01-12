#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

JAVA_DIRECTORY=/usr/local/lib
JAR_DIRECTORY=/usr/local/share
SCRIPT_DIRECTORY=/usr/local/bin

## remove java
if [ -d "${JAVA_DIRECTORY}/k3s-simplified-java" ]; then
    rm -rf "${JAVA_DIRECTORY}/k3s-simplified-java"
fi
## remove jar
rm "${JAR_DIRECTORY}/k3s-simplifier.jar"
## remove run script
rm "${SCRIPT_DIRECTORY}/k3s-simplifier"

rm install.sh
rm uninstall.sh
rm k3s-simplifier.jar
rm k3s-simplifier.sh

# Delete current directory if empty
current_dir=$(pwd)

# Count the number of files and directories in the current directory
num_items=$(ls -1A "$current_dir" | wc -l)

# Check if the directory is empty
if [ $num_items -eq 0 ]; then
    # If empty, go up a directory and delete the empty directory
    cd ..
    rmdir "$current_dir"
fi