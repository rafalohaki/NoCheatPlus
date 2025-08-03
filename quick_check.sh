#!/bin/bash
echo "??? Quick MC Plugin Check ???"
echo "Errors: $(grep -r "ERROR\|Exception" . --include="*.java" | wc -l)"
echo "Warnings: $(grep -r "WARNING\|@Deprecated" . --include="*.java" | wc -l)" 
echo "TODOs: $(grep -r "TODO\|FIXME" . --include="*.java" | wc -l)"
echo "Recent changes: $(find . -name "*.java" -mtime -1 | wc -l) files modified today"
