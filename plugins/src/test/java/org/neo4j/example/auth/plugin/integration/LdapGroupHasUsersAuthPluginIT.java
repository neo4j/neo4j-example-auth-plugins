/**
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.example.auth.plugin.integration;

import com.neo4j.configuration.SecuritySettings;
import com.neo4j.test.TestEnterpriseDatabaseManagementServiceBuilder;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.annotations.LoadSchema;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.Description;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.List;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.example.auth.plugin.ldap.LdapGroupHasUsersAuthPlugin;
import org.neo4j.internal.helpers.HostnamePort;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;
import org.neo4j.test.utils.TestDirectory;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.fail;
import static org.neo4j.configuration.connectors.BoltConnector.DEFAULT_PORT;

@CreateDS(
        name = "Test",
        partitions = { @CreatePartition(
                name = "example",
                suffix = "dc=example,dc=com" )
        },
        loadedSchemas = {
                @LoadSchema( name = "nis" ),
        } )
@CreateLdapServer(
        transports = { @CreateTransport( protocol = "LDAP", port = 10389, address = "localhost" ) }
)
@ApplyLdifFiles( "ldap_group_has_users_test_data.ldif" )
@TestDirectoryExtension
public class LdapGroupHasUsersAuthPluginIT extends AbstractLdapTestUnit
{
    @Inject
    private TestDirectory testDirectory;

    private static final Config config = Config.builder().withLogging( Logging.none() ).withoutEncryption().build();

    private DatabaseManagementService databases;
    private ConnectorPortRegister connectorPortRegister;

    @BeforeAll
    public static void beforeClass() throws Exception {
        processLdapAnnotations( LdapGroupHasUsersAuthPluginIT.class );
    }

    @BeforeEach
    public void setup() throws Exception
    {
        getLdapServer().setConfidentialityRequired( false );
        Neo4jLayout home = Neo4jLayout.of( testDirectory.homePath() );

        // Create directories and write out test config file
        File configDir = new File( home.homeDirectory().toFile(), "conf" );
        configDir.mkdirs();

        try ( FileWriter fileWriter = new FileWriter( new File( configDir, "ldap.conf" ) ) )
        {
            fileWriter.write( LdapGroupHasUsersAuthPlugin.LDAP_SERVER_URL_SETTING + "=ldap://localhost:10389" );
        }

        // Start up server with authentication enabled
        databases = new TestEnterpriseDatabaseManagementServiceBuilder( home )
                .setConfig( GraphDatabaseSettings.auth_enabled, true )
                .setConfig( SecuritySettings.authentication_providers, List.of( "plugin-" + LdapGroupHasUsersAuthPlugin.PLUGIN_NAME ) )
                .setConfig( SecuritySettings.authorization_providers, List.of( "plugin-" + LdapGroupHasUsersAuthPlugin.PLUGIN_NAME ) )
                .setConfig( BoltConnector.enabled, true )
                .setConfig( BoltConnector.listen_address, new SocketAddress( "localhost", DEFAULT_PORT ) )
                .build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) databases.database( GraphDatabaseSettings.DEFAULT_DATABASE_NAME );
        connectorPortRegister = db.getDependencyResolver().resolveDependency( ConnectorPortRegister.class );
    }


    @AfterEach
    public void tearDown()
    {
        databases.shutdown();
    }

    @Test
    public void shouldBeAbleToLoginAndAuthorizeWithLdapGroupHasUsersAuthPlugin()
    {
        // Login and create node with publisher user
        try( Driver driver = GraphDatabase.driver( boltURI(),
                AuthTokens.basic( "tank", "abc123" ), config );
             Session session = driver.session() )
        {
            Value single = session.run( "CREATE (n) RETURN count(n)" ).single().get( 0 );
            assertThat( single.asLong(), equalTo( 1L ) );
        }

        // Login with reader user
        try( Driver driver = GraphDatabase.driver( boltURI(),
                AuthTokens.basic( "neo", "abc123" ), config );
             Session session = driver.session() )
        {
            // Read query should succeed
            Value single = session.run( "MATCH (n) RETURN count(n)" ).single().get( 0 );
            assertThat( single.asLong(), greaterThanOrEqualTo( 1L ) );

            // Write query should fail
            try
            {
                session.run( "CREATE (n) RETURN count(n)" ).single().get( 0 );
                fail( "Should not be possible to create node using reader user" );
            }
            catch ( ClientException e )
            {
                assertThat( e.getMessage(), startsWith( "Create node with labels '' on database 'neo4j' is not allowed" ) );
            }
        }
    }

    private URI boltURI()
    {
        HostnamePort hostPort = connectorPortRegister.getLocalAddress( BoltConnector.NAME );
        return URI.create( "bolt" + "://" + hostPort + "/" );
    }

    private static void processLdapAnnotations( Class<?> clazz ) throws Exception
    {
        Description description = Description.createSuiteDescription( clazz.getSimpleName(), clazz.getAnnotations() );
        service = DSAnnotationProcessor.getDirectoryService( description );
        DSAnnotationProcessor.applyLdifs( description, service );
        ldapServer = ServerAnnotationProcessor.createLdapServer( description, service );
    }
}
