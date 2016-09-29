# neo4j-example-auth-plugins
Example authentication and authorization plugins for Neo4j

You have to run this with Java 8.

If you just want to build the plugins without the hassle described below,
you can choose to ignore integration tests by running:

    mvn clean install -DskipITs 

To compile the code and run all tests, you first need to prepare the `neokit` submodule which is used for integration tests:

    git submodule init
    git submodule update

For the integration tests to actually work you need Neo4j installed in:

    target/neo4j/neo4jhome
    
There is a script that you can use to do this manually on Linux or Mac:
(It takes a neo4j package tarball as the first parameter or from the environment variable `NEO4J_ARCHIVE`)

    ./reinstall-neo4j.sh neo4j-enterprise-<VERSION>-unix.tar.gz

Or you can setup some environment variables to have Neo4j automatically downloaded and installed by `neokit`:
 
    TEAMCITY_NEO4J_31NIGHTLY=https://build.neohq.net/repository/download/Drivers_Neo4jArtifacts_Neo4j31artifacts/lastSuccessful/neo4j-artifacts/neo4j-enterprise-3.1-NIGHTLY-unix.tar.gz
    TEAMCITY_USER=???
    TEAMCITY_PASSWORD=???

Then finally build with: 

    mvn clean install -Dneorun.start.args=”-n 3.1”

(NOTE: `neokit` also requires python to be installed)

## Install plugins in Neo4j
Copy the output jar file into the plugins folder of Neo4j Enterprise Edition 3.1 or later:

    cp plugins/target/neo4j-example-auth-plugins-<VERSION>.jar <NEO4J-HOME>/plugins/

Edit the Neo4j configuration file `<NEO4J-HOME>/conf/neo4j.conf` and add the `dbms.security.realm` setting, e.g.:

    dbms.security.realm=plugin-org.neo4j.example.auth.plugin.MyAuthPlugin

You can also enable multiple plugins simultaneously with the `dbms.security.realms` setting, e.g.:

    dbms.security.realms=plugin-MyAuthPlugin1,plugin-MyAuthPlugin2

You can also toggle authentication and authorization enabled individually, e.g.: 
 
    dbms.security.realms.plugin.authentication_enabled=true
    dbms.security.realms.plugin.authorization_enabled=false
    
(NOTE: This will currently not work with a plugin implementing the simplified `AuthPlugin` interface,
since it will not be loaded unless both settings are either `true` or left out)
