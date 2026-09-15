@rem
@echo off

rem NiceTime Gradle wrapper entry point. Generated manually to avoid a local Gradle install.

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java.exe
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JAVA_EXE=%%D\bin\java.exe
)

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
