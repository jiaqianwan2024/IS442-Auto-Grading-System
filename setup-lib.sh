#!/bin/bash
# setup-lib.sh
# Run this ONCE after cloning to download all required jars into lib/
# Uses curl which is available on Mac and Linux by default

echo ""
echo "============================================================"
echo " IS442 Auto-Grading System -- Dependency Setup"
echo "============================================================"
echo ""

mkdir -p lib

M="https://repo1.maven.org/maven2"

get() {
    FILENAME=$1
    URL=$2
    if [ -f "lib/$FILENAME" ]; then
        echo "[SKIP]     $FILENAME already exists"
    else
        echo "[GET]      $FILENAME"
        curl -L --silent --show-error -o "lib/$FILENAME" "$URL"
        if [ $? -ne 0 ]; then
            echo "[ERROR]    Failed to download $FILENAME - check internet connection"
        fi
    fi
}

get poi-5.2.3.jar                $M/org/apache/poi/poi/5.2.3/poi-5.2.3.jar
get poi-ooxml-5.2.3.jar          $M/org/apache/poi/poi-ooxml/5.2.3/poi-ooxml-5.2.3.jar
get poi-ooxml-full-5.2.3.jar     $M/org/apache/poi/poi-ooxml-full/5.2.3/poi-ooxml-full-5.2.3.jar
get xmlbeans-5.1.1.jar           $M/org/apache/xmlbeans/xmlbeans/5.1.1/xmlbeans-5.1.1.jar
get commons-compress-1.21.jar    $M/org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar
get commons-collections4-4.4.jar $M/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar
get commons-codec-1.15.jar       $M/commons-codec/commons-codec/1.15/commons-codec-1.15.jar
get commons-io-2.11.0.jar        $M/commons-io/commons-io/2.11.0/commons-io-2.11.0.jar
get curvesapi-1.07.jar           $M/com/github/virtuald/curvesapi/1.07/curvesapi-1.07.jar
get log4j-api-2.20.0.jar         $M/org/apache/logging/log4j/log4j-api/2.20.0/log4j-api-2.20.0.jar
get log4j-core-2.20.0.jar        $M/org/apache/logging/log4j/log4j-core/2.20.0/log4j-core-2.20.0.jar

echo ""
echo "============================================================"
echo " Done! Now run:"
echo "   chmod +x compile.sh run.sh"
echo "   ./compile.sh"
echo "   ./run.sh"
echo "============================================================"
echo ""