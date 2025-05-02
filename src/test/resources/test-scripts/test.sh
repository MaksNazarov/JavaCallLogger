#!/bin/bash

APP_NAME="simple-app"
MAIN_CLASS="simpleapp.Main"
OUTPUT_FILE="calls.txt"

TEST_APPS_DIR="src/test/resources/test-apps"
APP_DIR="$TEST_APPS_DIR/$APP_NAME"
INPUT_JAR="$APP_DIR/app.jar"
MODIFIED_JAR="$APP_DIR/modified_app.jar"

# compile test application's base JAR
echo "Compiling $APP_NAME..."
mkdir -p "$APP_DIR/target/classes"

JAVA_FILES=$(find "$APP_DIR/src" -name "*.java")
if [ -z "$JAVA_FILES" ]; then
    echo "No .java files found in $APP_DIR/src"
    exit 1
fi

javac -d "$APP_DIR/target/classes" $JAVA_FILES || { echo "Compilation failed"; exit 1; }

echo "Creating JAR for $APP_NAME..."
echo "Main-Class: $MAIN_CLASS" > "$APP_DIR/manifest.txt"
jar cvfm "$INPUT_JAR" "$APP_DIR/manifest.txt" -C "$APP_DIR/target/classes" . || { echo "JAR creation failed"; exit 1; }

# build and run instrumentation tool
echo "Running instrumentation tool..."
mvn clean package || { echo "Maven build failed"; exit 1; }

# TODO: jar name to config options?
java -jar target/JAR_graph_collector-1.0-SNAPSHOT.jar \
  "$INPUT_JAR" \
  "$MODIFIED_JAR" \
  "$OUTPUT_FILE" || { echo "Instrumentation failed"; exit 1; }

echo "Running instrumented JAR..."
java -jar "$MODIFIED_JAR" || { echo "Execution failed"; exit 1; }

# TODO compare to calls.txt.expected in the app directory
echo "Call graph written to $OUTPUT_FILE:"
cat "$OUTPUT_FILE"