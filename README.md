# neo4j-example-auth-plugins
Example authentication and authorization plugins for Neo4j

You have to run this with Java 11.

If you just want to build the plugins, you can choose to ignore integration tests by running:

    mvn clean install -DskipITs 

## Install plugins in Neo4j
Copy the output jar file into the plugins folder of Neo4j Enterprise Edition 4.0 or later:

    cp plugins/target/neo4j-example-auth-plugins-<VERSION>.jar <NEO4J-HOME>/plugins/

Edit the Neo4j configuration file `<NEO4J-HOME>/conf/neo4j.conf` and add the `dbms.security.authentication_providers` 
and `dbms.security.authorization_providers` settings, e.g.:

    dbms.security.authentication_providers=plugin-org.neo4j.example.auth.plugin.MyAuthPlugin
    dbms.security.authorization_providers=plugin-org.neo4j.example.auth.plugin.MyAuthPlugin

You can also enable multiple plugins simultaneously e.g.:

    dbms.security.authentication_providers=plugin-MyAuthPlugin1,plugin-MyAuthPlugin2

You can also toggle authentication and authorization enabled individually by only adding it to either of the settings: 

    dbms.security.authentication_providers=plugin-MyAuthPlugin1
    dbms.security.authorization_providers=plugin-MyAuthPlugin2

(NOTE: Any plugin implementing the simplified `AuthPlugin` interface must be in both `dbms.security.authentication_providers`
 and `dbms.security.authorization_providers`, or it will not be loaded)
