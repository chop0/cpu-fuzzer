#!/usr/bin/env bash

java --add-opens=java.base/java.util=com.fasterxml.jackson.databind --enable-native-access=ax.xz.fuzz --enable-preview -Djava.library.path=. -p antlr-4.13.1-complete.jar:iced-x86-1.21.0.jar:jackson-annotations-2.17.2.jar:jackson-core-2.17.2.jar:jackson-databind-2.17.2.jar:jackson-dataformat-xml-2.17.2.jar:stax2-api-4.2.2.jar:woodstox-core-7.0.0.jar:out -m ax.xz.fuzz/ax.xz.fuzz.runtime.Master "$@"
exit $?