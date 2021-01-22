#!/bin/sh

if [ -z "$GRAALVM_HOME" ]; then
    echo "The GRAALVM_HOME environment variable is required.\n\n    e.g. export GRAALVM_HOME=../graalvm-ce-java11-21.0.0/\n" 1>&2
    exit 1
fi
export JAVA_HOME=$GRAALVM_HOME

./gradlew clean build

RUNTIME_CLASSPATH=$(./gradlew -q printRuntimeClasspath)

mkdir -p build/native-image

echo Compiling native-image...
$GRAALVM_HOME/bin/native-image \
	-cp "$RUNTIME_CLASSPATH"\
	-H:Path=build/native-image\
	-H:Name=signal-cli\
	-H:JNIConfigurationFiles=\
	-H:DynamicProxyConfigurationFiles=\
	-H:ReflectionConfigurationFiles=\
	-H:ResourceConfigurationFiles=\
	--no-fallback\
	--allow-incomplete-classpath\
	--report-unsupported-elements-at-runtime\
	--enable-url-protocols=http,https\
	--enable-https\
	--enable-all-security-services\
	-H:ResourceConfigurationFiles=graalvm-config-dir/resource-config.json\
	-H:ReflectionConfigurationFiles=graalvm-config-dir/reflect-config.json\
	org.asamk.signal.Main

