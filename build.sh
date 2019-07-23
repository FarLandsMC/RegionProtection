#!/bin/bash

# Constants
SOURCE_DIR="./src"
BUILD_DIR="./build"
LIB_DIR="./lib"
DIST_DIR="./out"
SPIGOT_VERSION="1.14.4"
FILE_NAME="RegionProtection.jar"

# Preliminary cleaning
echo "Performing preliminary cleaning"
if [ -d $BUILD_DIR ]; then rm -rf $BUILD_DIR; fi
rm -f $DIST_DIR/$FILE_NAME

# Ensure the spigot jar exists
if [ ! -f $LIB_DIR/spigot-${SPIGOT_VERSION}.jar ]; then
    echo "Spigot jar not found. Use Ctrl+C to prevent this script from building the jar." \
    "If you wish for this script to automatically build the spigot jar, please make sure your IDE is closed while this is happening." \
    "Spigot will be built in 15 seconds."
    sleep 15
    echo "Building spigot jar. This may take several minutes."
    rm -rf $LIB_DIR
    mkdir $LIB_DIR
    cd $LIB_DIR
    wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    java -jar BuildTools.jar --rev $SPIGOT_VERSION
    find . -not -name "spigot-${SPIGOT_VERSION}.jar" -delete
    cd ..
fi

# Compilation
mkdir $BUILD_DIR

echo "Compiling source files."
find $SOURCE_DIR -name "*.java" > .sources.txt
javac -cp $LIB_DIR/spigot-${SPIGOT_VERSION}.jar @.sources.txt -d $BUILD_DIR

echo "Copying resources."
find $SOURCE_DIR -type f -not -name "*.java" -exec cp {} $BUILD_DIR \;

echo "Building jar."
mkdir -p $DIST_DIR
jar cf $DIST_DIR/$FILE_NAME -C $BUILD_DIR .

# Cleaning
rm .sources.txt
rm -rf $BUILD_DIR

# Success
echo "Compilation successful. Output: ${DIST_DIR}/${FILE_NAME}"