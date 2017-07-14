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

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.annotations.LoadSchema;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.example.auth.plugin.ldap.LdapGroupHasUsersAuthPlugin;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.internal.EnterpriseInProcessServerBuilder;
import org.neo4j.test.rule.TestDirectory;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.fail;

@RunWith( FrameworkRunner.class )
@CreateDS(
        name = "Test",
        partitions = { @CreatePartition(
                name = "example",
                suffix = "dc=example,dc=com" )
        },
        loadedSchemas = {
                @LoadSchema( name = "nis", enabled = true ),
        } )
@CreateLdapServer(
        transports = { @CreateTransport( protocol = "LDAP", port = 10389, address = "localhost" ) }
)
@ApplyLdifFiles( "ldap_group_has_users_test_data.ldif" )
public class LdapGroupHasUsersAuthPluginIT extends AbstractLdapTestUnit
{
    @Rule
    public final TestDirectory testDirectory = TestDirectory.testDirectory();

    private ServerControls server;

    @Before
    public void setup() throws Exception
    {
        getLdapServer().setConfidentialityRequired( false );

        // Create directories and write out test config file
        File directory = testDirectory.directory();
        File configDir = new File( directory, "test/databases/graph.db/conf" );
        configDir.mkdirs();

        try ( FileWriter fileWriter = new FileWriter( new File( configDir, "ldap.conf" ) ) )
        {
            fileWriter.write( LdapGroupHasUsersAuthPlugin.LDAP_SERVER_URL_SETTING + "=ldap://localhost:10389" );
        }

        // Start up server with authentication enables
        server = new EnterpriseInProcessServerBuilder( directory, "test" )
                .withConfig( "dbms.security.auth_enabled", "true" )
                .withConfig( "dbms.security.auth_provider", "plugin-" + LdapGroupHasUsersAuthPlugin.PLUGIN_NAME )
                .newServer();
    }

    @After
    public void tearDown() throws Exception
    {
        server.close();
    }

    @Test
    public void shouldBeAbleToLoginAndAuthorizeWithLdapGroupHasUsersAuthPlugin() throws Throwable
    {
        // Login and create node with publisher user
        try( Driver driver = GraphDatabase.driver( server.boltURI(),
                AuthTokens.basic( "tank", "abc123" ) );
             Session session = driver.session() )
        {
            Value single = session.run( "CREATE (n) RETURN count(n)" ).single().get( 0 );
            assertThat( single.asLong(), equalTo( 1L ) );
        }

        // Login with reader user
        try( Driver driver = GraphDatabase.driver( server.boltURI(),
                AuthTokens.basic( "neo", "abc123" ) );
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
                assertThat( e.getMessage(), startsWith( "Write operations are not allowed" ) );
            }
        }
    }
}
