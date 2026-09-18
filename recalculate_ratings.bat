@echo off
setlocal
echo ========================================================
echo  Recalculating and Overwriting Ratings in PostgreSQL
echo ========================================================

cd /d "%~dp0demo"
call mvn clean test -Dtest=RunPriorRatingsDemoTest

echo.
echo Completed recalculation run.
pause
