@echo off
cd c:\temp\

set /p UserInputPackage= Enter the package name: 
set /p UserInputDB= Enter the database name: 

@echo on
adb shell "run-as %UserInputPackage% chmod 666 /data/data/%UserInputPackage%/databases/%UserInputDB%.sqlite"
adb pull /data/data/%UserInputPackage%/databases/%UserInputDB%.sqlite
@echo off
pause