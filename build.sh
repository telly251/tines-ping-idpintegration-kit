#!/bin/bash
#
# Build script for Tines IDP Adapter
#

set -e

echo "========================================="
echo "Building Tines IDP Adapter for PingFederate"
echo "========================================="

# Check for Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Check for Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "11" ]; then
    echo "ERROR: Java 11 or later is required (found Java $JAVA_VERSION)"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -1)"
echo "Maven version: $(mvn -version | head -1)"
echo ""

# Check if PF SDK is available
if [ ! -f lib/pf-protocolengine.jar ]; then
    echo "WARNING: PingFederate SDK not found in lib/"
    echo ""
    echo "To build successfully, you need to either:"
    echo "1. Copy pf-protocolengine.jar from your PingFederate installation"
    echo "   to the lib/ directory"
    echo ""
    echo "   OR"
    echo ""
    echo "2. Install the JAR to your local Maven repository:"
    echo "   mvn install:install-file \\"
    echo "     -Dfile=/path/to/pf-protocolengine.jar \\"
    echo "     -DgroupId=com.pingidentity.pingfederate \\"
    echo "     -DartifactId=pf-protocolengine \\"
    echo "     -Dversion=11.3.0 \\"
    echo "     -Dpackaging=jar"
    echo ""
    echo "The JAR is typically located at:"
    echo "  <PF_INSTALL>/pingfederate/server/default/lib/pf-protocolengine.jar"
    echo ""
fi

# Clean and build
echo "Running Maven build..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "Build successful!"
    echo "========================================="
    echo ""
    echo "Output JAR: target/tines-idp-adapter-1.0.0.jar"
    echo ""
    echo "To deploy:"
    echo "1. Copy the JAR to your PingFederate server:"
    echo "   cp target/tines-idp-adapter-1.0.0.jar <PF_INSTALL>/pingfederate/server/default/deploy/"
    echo ""
    echo "2. Restart PingFederate"
    echo ""
else
    echo ""
    echo "Build failed! Check the output above for errors."
    exit 1
fi
