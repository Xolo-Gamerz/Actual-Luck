@echo off
set APP_HOME=%~dp0
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  echo Missing gradle\wrapper\gradle-wrapper.jar. Generate wrapper files with: gradle wrapper
  exit /b 1
)
java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
