#!/bin/bash

#######################
# Set neo4j archive

if [ -n "$1" ] ; then
    NEO4J_ARCHIVE="$1"
fi

if [ -z "$NEO4J_ARCHIVE" ] ; then
    # Try some defaults
    NEO4J_ARCHIVE="neo4j-enterprise-3.1.0-unix.tar.gz"
    if [ ! -f "$NEO4J_ARCHIVE" ] ; then
        NEO4J_ARCHIVE="neo4j-enterprise-3.1.0-SNAPSHOT-unix.tar.gz"
        if [ ! -f "$NEO4J_ARCHIVE" ] ; then
            NEO4J_ARCHIVE="neo4j-enterprise-3.1.0-M10-unix.tar.gz"
        fi
    fi
fi

if [ -z "$NEO4J_ARCHIVE" ] || [ ! -f "$NEO4J_ARCHIVE" ] ; then
    echo "Error: could not find the Neo4j package tarball to install:"
    if [ -n "$NEO4J_ARCHIVE" ] ; then
        echo $NEO4J_ARCHIVE
    else
        echo "Either specify it as the first parameter or set the environment variable NEO4J_ARCHIVE."
    fi
    exit 1
else
    echo "Neo4j package tarball to install: $NEO4J_ARCHIVE"
fi

#######################
# Set neo4j home

if [ -z "$NEO4J_HOME_NAME" ] ; then
    NEO4J_HOME_NAME="$2"
    if [ -z "$NEO4J_HOME_NAME" ] ; then
        NEO4J_HOME_NAME="neo4jhome"
    fi
fi

#######################
# Go to target/neo4j

[ -d "target" ] || mkdir target
[ -d "target/neo4j" ] || mkdir target/neo4j

pushd target/neo4j

#######################
# Remove the old neo4j

if [ -d "$NEO4J_HOME_NAME" ] ; then
    echo "Removing old $NEO4J_HOME_NAME..."
    rm -r $NEO4J_HOME_NAME
fi

#######################
# Install the new neo4j

echo "Extracting $NEO4J_ARCHIVE into target/neo4j/$NEO4J_HOME_NAME"
mkdir $NEO4J_HOME_NAME && tar zxf ../../$NEO4J_ARCHIVE -C $NEO4J_HOME_NAME --strip-components 1

popd
