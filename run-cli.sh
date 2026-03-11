#!/bin/bash
echo "============================================"
echo " IS442 AUTO-GRADING SYSTEM — CLI MODE"
echo "============================================"
echo ""
echo "Running grading pipeline in console mode..."
echo "No web server will start. Output shown below."
echo ""
mvn spring-boot:run -Dspring-boot.run.profiles=cli -Dspring-boot.run.arguments="--cli"
