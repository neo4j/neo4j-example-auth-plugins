# neo4j-example-auth-plugins
Example authentication and authorization plugins for Neo4j

To compile the code and run all tests, if you are running Java 8:

    `mvn clean install`

## Install plugins in Neo4j
Copy the output jar file into the plugins folder of Neo4j Enterprise Edition 3.1 or later:

    `cp plugins/target/neo4j-example-auth-plugins-<VERSION>.jar <NEO4J-HOME>/plugins/`

Edit the Neo4j configuration file `<NEO4J-HOME>/conf/neo4j.conf` and add the `dbms.security.realm` setting, e.g.:

    `dbms.security.realm=plugin-org.neo4j.example.auth.MyAuthPlugin`

You can also enable multiple plugins simultaneously with the `dbms.security.realms` setting, e.g.:

    `dbms.security.realms=plugin-org.neo4j.example.auth.MyAuthPlugin1,plugin-org.neo4j.example.auth.MyAuthPlugin2`

You can also toggle authentication and authorization enabled individually, e.g.:

    `dbms.security.realms.plugin.authentication_enabled=true`
    `dbms.security.realms.plugin.authorization_enabled=false`
