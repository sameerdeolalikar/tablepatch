#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then
    javac_cmd="$JAVA_HOME/bin/javac"
    jar_cmd="$JAVA_HOME/bin/jar"
    java_cmd="$JAVA_HOME/bin/java"
else
    javac_cmd=javac
    jar_cmd=jar
    java_cmd=java
fi
mkdir -p build/classes build/test-classes
"$javac_cmd" --release 21 -Xlint:all -Werror -d build/classes src/main/java/tablepatch/*.java
"$jar_cmd" --create --file build/tablepatch.jar --main-class tablepatch.Main -C build/classes .
if [ "${1:-}" = test ]; then
    "$javac_cmd" --release 21 -Xlint:all -Werror -cp build/classes -d build/test-classes src/test/java/tablepatch/*.java
    "$java_cmd" -cp build/classes:build/test-classes tablepatch.Tests
fi
