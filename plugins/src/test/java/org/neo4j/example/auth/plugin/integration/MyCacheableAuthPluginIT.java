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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.example.auth.plugin.MyCacheableAuthPlugin;
import org.neo4j.harness.Neo4j;

import static com.neo4j.harness.EnterpriseNeo4jBuilders.newInProcessBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

public class MyCacheableAuthPluginIT
{
    private static final Config config = Config.builder().withLogging( Logging.none() ).withoutEncryption().build();
    private Neo4j server;

    @BeforeEach
    public void setUp()
    {
        // Start up server with authentication enables
        server = newInProcessBuilder()
                .withConfig( GraphDatabaseSettings.auth_enabled, true )
                .withConfig( SecuritySettings.authentication_providers, List.of( "plugin-org.neo4j.example.auth.plugin.MyCacheableAuthPlugin" ) )
                .withConfig( SecuritySettings.authorization_providers, List.of( "plugin-org.neo4j.example.auth.plugin.MyCacheableAuthPlugin" ) )
                .build();
    }

    @AfterEach
    public void tearDown()
    {
        if (server != null)
        {
            server.close();
        }
    }

    @Test
    public void shouldAuthenticateNeo4jUser()
    {
        // When & Then
        try ( Driver driver = GraphDatabase.driver( server.boltURI(),
                                                    AuthTokens.basic( "neo4j", "neo4j" ), config );
              Session session = driver.session() )
        {
            Value single = session.run( "RETURN 1" ).single().get( 0 );
            assertThat( single.asLong(), equalTo( 1L ) );
        }
    }

    @Test
    public void shouldAuthenticateAndAuthorizeKalleMoraeusAsAdmin()
    {
        MyCacheableAuthPlugin.authCounter.set( 0 );
        try ( Driver driver = GraphDatabase.driver( server.boltURI(), AuthTokens.basic( "moraeus", "suearom" ), config ) )
        {
            try ( Session session = driver.session() )
            {
                session.run( "CREATE (a:Person {name:'Kalle Moraeus', title:'Riksspelman'})" );
            }
        }
        try ( Driver driver = GraphDatabase.driver( server.boltURI(), AuthTokens.basic( "moraeus", "suearom" ), config ) )
        {
            try ( Session session = driver.session() )
            {
                Result result =
                        session.run( "MATCH (a:Person) WHERE a.name = 'Kalle Moraeus' RETURN a.name AS name, a.title AS title" );
                assertTrue( result.hasNext() );
                while ( result.hasNext() )
                {
                    Record record = result.next();
                    assertThat( record.get( "name" ).asString(), equalTo( "Kalle Moraeus" ) );
                    assertThat( record.get( "title" ).asString(), equalTo( "Riksspelman" ) );
                    System.out.println( record.get( "title" ).asString() + " " + record.get( "name" ).asString() );
                }
            }
        }
        assertThat( MyCacheableAuthPlugin.authCounter.get(), equalTo( 1 ) );
    }
}
