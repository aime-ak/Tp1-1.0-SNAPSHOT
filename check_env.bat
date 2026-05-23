@echo off
echo === STEP 1: Checking for PDF files ===
dir *.pdf 2>&1
echo.
echo === STEP 2: Checking GEMINI_KEY ===
if defined GEMINI_KEY (
    echo GEMINI_KEY is set
) else (
    echo GEMINI_KEY is not set
)
echo.
echo === STEP 3: Java Version ===
java -version 2>&1
echo.
echo === STEP 4: Maven Version ===
mvn -version 2>&1
echo.
echo === STEP 5: Building with mvn clean compile ===
mvn clean compile 2>&1
