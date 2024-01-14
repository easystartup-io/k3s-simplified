#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Detect OS and Architecture
OS=$(uname -s)
ARCH=$(uname -m)

echo "Detected OS: $OS, Architecture: $ARCH"

JAVA_DIRECTORY=/usr/local/lib
JAR_DIRECTORY=/usr/local/share
SCRIPT_DIRECTORY=/usr/local/bin

BASE_URL="https://download.oracle.com/java/21/archive/jdk-21.0.1"
# Initialize DOWNLOAD_URL variable
DOWNLOAD_URL=""

# Path to the Java installation directory
JAVA_DIR_LOCAL_BASE=$(pwd)/bundled-jre

if [ "$OS" = "Linux" ] && [ "$ARCH" = "aarch64" ]; then
    DOWNLOAD_URL="${BASE_URL}_linux-aarch64"
elif [ "$OS" = "Linux" ] && [ ! "$ARCH" = "aarch64" ]; then
    DOWNLOAD_URL="${BASE_URL}_linux-x64"
elif [ "$OS" = "Darwin" ] && [ "$ARCH" = "arm64" ]; then
    DOWNLOAD_URL="${BASE_URL}_macos-aarch64";
    JAVA_DIR_LOCAL_BASE="${JAVA_DIR_LOCAL_BASE}/jdk-21.0.1.jdk/Contents/Home";
elif [ "$OS" = "Darwin" ] && [ ! "$ARCH" = "arm64" ]; then
    DOWNLOAD_URL="${BASE_URL}_macos-x64";
    JAVA_DIR_LOCAL_BASE="${JAVA_DIR_LOCAL_BASE}/jdk-21.0.1.jdk/Contents/Home";
else
    echo "Unsupported OS or Architecture"
    exit 1
fi

DOWNLOAD_URL="${DOWNLOAD_URL}_bin.tar.gz"

# Check if Java is already installed
if [ -d "${JAVA_DIRECTORY}/k3s-simplified-java" ]; then
    echo "Java is already installed."
else
    echo "Downloading OpenJDK 21..."
    wget -O openjdk-21.tar.gz $DOWNLOAD_URL

    mkdir -p bundled-jre

    echo "Extracting OpenJDK 21..."

    tar -xzf openjdk-21.tar.gz -C bundled-jre --strip-components=1
    rm openjdk-21.tar.gz
fi

mkdir -p $JAR_DIRECTORY
mkdir -p $JAVA_DIRECTORY
mkdir -p $SCRIPT_DIRECTORY

# move items

echo "Moving java to ${JAVA_DIRECTORY}/k3s-simplified-java"
if [ ! -d "${JAVA_DIRECTORY}/k3s-simplified-java" ]; then
    mv "${JAVA_DIR_LOCAL_BASE}" "${JAVA_DIRECTORY}/k3s-simplified-java"
fi

echo "Moving jar to ${JAR_DIRECTORY}/k3s-simplified.jar"
mv k3s-simplified.jar "${JAR_DIRECTORY}/k3s-simplified.jar"

echo "Moving CLI binary script to ${SCRIPT_DIRECTORY}/k3s-simplified"
mv k3s-simplified.sh "${SCRIPT_DIRECTORY}/k3s-simplified"
chmod +x "${SCRIPT_DIRECTORY}/k3s-simplified"

rm install.sh
rm uninstall.sh

# Delete current directory if empty
current_dir=$(pwd)

# Count the number of files and directories in the current directory
num_items=$(ls -1A "$current_dir" | wc -l)

# Check if the directory is empty
if [ $num_items -eq 0 ]; then
    # If empty, go up a directory and delete the empty directory
    cd ..
#    rmdir "$current_dir"
fi

printf "\nInstallation completed successfully...\n"
printf "\nTo get started just run 'k3s-simplified'"