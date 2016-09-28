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

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.util.Neo4jSettings;
import org.neo4j.driver.v1.util.TestNeo4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class MyAuthPluginIT
{
    @BeforeClass
    public static void setUp()
    {
        System.setProperty( "neorun.start.args", "-n 3.1" );
    }

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public TestNeo4j neo4j = new TestNeo4j();

    @Test
    public void shouldAuthenticateNeo4jUser() throws Throwable
    {
        // Given
        restartWithAuthEnabled();

        // When & Then
        try( Driver driver = GraphDatabase.driver( neo4j.uri(),
             AuthTokens.basic( "neo4j", "neo4j" ) );
             Session session = driver.session() )
        {
            Value single = session.run( "RETURN 1" ).single().get( 0 );
            assertThat( single.asLong(), equalTo( 1L ) );
        }
    }

    @Test
    public void shouldAuthenticateAndAuthorizeKalleMoraeusAsAdmin() throws Exception
    {
        restartWithAuthEnabled();

        Driver driver = GraphDatabase.driver( "bolt://localhost", AuthTokens.basic( "moraeus", "suearom" ) );
        Session session = driver.session();

        session.run( "CREATE (a:Person {name:'Kalle Moraeus', title:'Riksspelman'})" );

        StatementResult result =
                session.run( "MATCH (a:Person) WHERE a.name = 'Kalle Moraeus' RETURN a.name AS name, a.title AS title" );
        while ( result.hasNext() )
        {
            Record record = result.next();
            System.out.println( record.get( "title" ).asString() + " " + record.get( "name" ).asString() );
        }

        session.close();
        driver.close();
    }

    private void restartWithAuthEnabled() throws Exception
    {
        neo4j.restart( Neo4jSettings.TEST_SETTINGS
                .updateWith( Neo4jSettings.AUTH_ENABLED, "true" )
                .updateWith( "dbms.security.realm", "plugin-org.neo4j.example.auth.plugin.MyAuthPlugin" )
                .updateWith( Neo4jSettings.DATA_DIR, tempDir.getRoot().getAbsolutePath().replace("\\", "/") ));
    }
}