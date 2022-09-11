#!/usr/bin/env bash

rm -rf target/
rm -rf resources/META-INF/native-image/tempConfig-*

# build java jar
clojure -X:uberjar

# build native image
"$GRAALVM_HOME/bin/native-image" \
    -jar "target/service.jar" \
    "target/service"
