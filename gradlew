#!/bin/sh
# Gradle Wrapper launcher. The official gradle-wrapper.jar must be placed in gradle/wrapper/.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Missing gradle/wrapper/gradle-wrapper.jar. Generate wrapper files with: gradle wrapper" >&2
  exit 1
fi
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
