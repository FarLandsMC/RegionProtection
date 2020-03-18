#!/bin/bash

# Configurables
SOURCE_DIR="./src"
LIB_DIR="./lib"
DIST_DIR="./out"
SPIGOT_VERSION="1.15.2"

# Non-configurables (transient)
BUILD_DIR="./build"
SPIGOT_DIR="./spigot"

# Input-based
if [[ -z "$1" ]]; then
    FILE_NAME="RegionProtection.jar"
else
    FILE_NAME="$1"
fi

# Preliminary cleaning
echo "Performing preliminary cleaning"
if [[ -d ${BUILD_DIR} ]]; then rm -rf ${BUILD_DIR}; fi
rm -f ${DIST_DIR}/${FILE_NAME}

# Ensure the spigot jar exists
if [[ ! -f ${LIB_DIR}/spigot-${SPIGOT_VERSION}.jar ]]; then
    echo "Spigot jar not found. Use Ctrl+C to prevent this script from building the jar automatically." \
    "If you wish for this script to build the spigot jar, please make sure your IDE is closed while this is happening." \
    "The jar will be built in 15 seconds."
    sleep 15
    echo "Building spigot jar. This may take several minutes."
    sleep 2

    # Prep operations
    rm -rf ${SPIGOT_DIR}
    mkdir ${SPIGOT_DIR}
    cd ${SPIGOT_DIR}

    # Get the build tools and build spigot
    wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    java -jar BuildTools.jar --rev ${SPIGOT_VERSION}

    # Cleaning and finalizing
    cd ..
    cp ${SPIGOT_DIR}/spigot-${SPIGOT_VERSION}.jar ${LIB_DIR}
    rm -rf ${SPIGOT_DIR}
fi

# Compilation
mkdir ${BUILD_DIR}

echo "Compiling source files."
find ${SOURCE_DIR} -name "*.java" > .sources.txt
javac -cp ${LIB_DIR}/spigot-${SPIGOT_VERSION}.jar @.sources.txt -d ${BUILD_DIR}

echo "Copying resources."
find ${SOURCE_DIR} -type f -not -name "*.java" -exec cp {} ${BUILD_DIR} \;

echo "Building jar."
mkdir -p ${DIST_DIR}
jar cMf ${DIST_DIR}/${FILE_NAME} -C ${BUILD_DIR} .

# Cleaning
rm .sources.txt
rm -rf ${BUILD_DIR}

# Success
echo "Compilation successful. Output: ${DIST_DIR}/${FILE_NAME}"