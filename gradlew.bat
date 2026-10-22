@echo off
setlocal

set DIR=%~dp0
set JAVA_EXE=java
set DEFAULT_JVM_OPTS=

rem Resolve the location of the wrapper JAR
set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo Gradle wrapper JAR not found at %WRAPPER_JAR%. It will be downloaded automatically if the wrapper is set up correctly.
)

%JAVA_EXE% %DEFAULT_JVM_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
