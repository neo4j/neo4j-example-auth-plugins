/**
 * Copyright (c) 2002-2016 "Neo Technology,"
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.util.Neo4jSettings;
import org.neo4j.driver.v1.util.TestNeo4j;
import org.neo4j.example.auth.plugin.ldap.LdapGroupHasUsersAuthPlugin;

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
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public TestNeo4j neo4j = new TestNeo4j();

    @Before
    public void setup()
    {
        getLdapServer().setConfidentialityRequired( false );
    }

    @Test
    public void shouldBeAbleToLoginAndAuthorizeWithLdapGroupHasUsersAuthPlugin() throws Throwable
    {
        restartWithAuthEnabled();

        // Login and create node with publisher user
        try( Driver driver = GraphDatabase.driver( neo4j.uri(),
                AuthTokens.basic( "tank", "abc123" ) );
             Session session = driver.session() )
        {
            Value single = session.run( "CREATE (n) RETURN count(n)" ).single().get( 0 );
            assertThat( single.asLong(), equalTo( 1L ) );
        }

        // Login with reader user
        try( Driver driver = GraphDatabase.driver( neo4j.uri(),
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

    private void restartWithAuthEnabled() throws Exception
    {
        neo4j.restart( Neo4jSettings.TEST_SETTINGS
                .updateWith( Neo4jSettings.AUTH_ENABLED, "true" )
                .updateWith( "dbms.security.auth_provider", "plugin-" + LdapGroupHasUsersAuthPlugin.PLUGIN_NAME )
                .updateWith( LdapGroupHasUsersAuthPlugin.LDAP_SERVER_URL_SETTING, "ldap://localhost:10389" )
                .updateWith( Neo4jSettings.DATA_DIR, tempDir.getRoot().getAbsolutePath().replace("\\", "/") ) );
    }
}
