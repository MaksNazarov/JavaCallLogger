#!/bin/bash

APP_NAME="lambda-app"
MAIN_CLASS="lambdaapp.Main"
OUTPUT_FILE="calls.txt"
MODE="$1"
DIFF_FILE="diff-output.txt"

TEST_APPS_DIR="src/test/resources/test-apps"
APP_DIR="$TEST_APPS_DIR/$APP_NAME"
INPUT_JAR="$APP_DIR/app.jar"
MODIFIED_JAR="$APP_DIR/modified_app.jar"
EXPECTED_FILE="$APP_DIR/calls.txt.expected"

compile_app() {
    echo "Compiling $APP_NAME..."
    mkdir -p "$APP_DIR/target/classes"

    JAVA_FILES=$(find "$APP_DIR/src" -name "*.java")
    if [ -z "$JAVA_FILES" ]; then
        echo "No .java files found in $APP_DIR/src"
        exit 1
    fi

    javac -d "$APP_DIR/target/classes" $JAVA_FILES || { echo "Compilation failed"; exit 1; }
}

create_jar() {
    echo "Creating JAR for $APP_NAME..."
    echo "Main-Class: $MAIN_CLASS" > "$APP_DIR/manifest.txt"
    jar cvfm "$INPUT_JAR" "$APP_DIR/manifest.txt" -C "$APP_DIR/target/classes" . || { echo "JAR creation failed"; exit 1; }
}

instrument_jar() {
    echo "Running instrumentation tool..."
    mvn clean package || { echo "Maven build failed"; exit 1; }

    # TODO: jar name to config options?
    java -jar target/JAR_graph_collector-1.0-SNAPSHOT.jar \
      "$INPUT_JAR" \
      "$MODIFIED_JAR" \
      "$OUTPUT_FILE" || { echo "Instrumentation failed"; exit 1; }
}

execute_instrumented_jar() {
    echo "Running instrumented JAR..."
    java -jar "$MODIFIED_JAR" || { echo "Execution failed"; exit 1; }
}

compare_output() {
    echo "Comparing generated output with expected..."
    if [ ! -f "$EXPECTED_FILE" ]; then
        echo "Expected file not found: $EXPECTED_FILE"
        exit 1
    fi

    # TODO: clean up created files
    # remove trailing spaces & newlines for better compatibility
    tr -d '\r' < "$OUTPUT_FILE" | sed 's/[ \t]*$//' | sed '/^\s*$/d' | sort > "${OUTPUT_FILE}.normalized"
    tr -d '\r' < "$EXPECTED_FILE" | sed 's/[ \t]*$//' | sed '/^\s*$/d' | sort > "${EXPECTED_FILE}.normalized"

    if diff "${OUTPUT_FILE}.normalized" "${EXPECTED_FILE}.normalized" > "$DIFF_FILE"; then
        echo "Output matches expected!"
        rm "$DIFF_FILE"
    else
        echo "Output does NOT match expected!"
        echo "Detailed diff written to $DIFF_FILE:"
        cat "$DIFF_FILE"
        exit 1
    fi
}

case "$MODE" in
    generate)
        compile_app
        create_jar
        instrument_jar
        execute_instrumented_jar
        echo "Call graph written to $OUTPUT_FILE:"
        cat "$OUTPUT_FILE"
        ;;

    check)
        compile_app
        create_jar
        instrument_jar
        execute_instrumented_jar
        compare_output
        ;;

    *)
        echo "Usage: $0 {generate|check}"
        exit 1
        ;;
esac