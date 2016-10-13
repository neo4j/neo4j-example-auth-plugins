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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;

import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.util.Neo4jSettings;
import org.neo4j.driver.v1.util.TestNeo4j;
import org.neo4j.example.auth.plugin.pki.PkiAuthPlugin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.fail;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.CRYPTO_ALGORITHM;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.DEFAULT_USER;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.ENCRYPTED_USERNAME_PARAMETER_NAME;
import static org.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles.ADMIN;
import static org.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles.READER;

public class PkiAuthPluginIT
{
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public TestNeo4j neo4j = new TestNeo4j();

    @Test
    public void authenticateDefaultUser() throws Throwable
    {
        KeyPair defaultUserKeys = generateKeyPair();

        restartWithAuthEnabled( defaultUserKeys.getPublic() );

        createNode( DEFAULT_USER, defaultUserKeys.getPrivate() );
    }

    @Test
    public void addUserPerformOperationAndRemoveUser() throws Throwable
    {
        KeyPair defaultUserKeys = generateKeyPair();

        restartWithAuthEnabled( defaultUserKeys.getPublic() );

        String testUser = "testUser";
        PrivateKey testUserPrivateKey = addNewUser( defaultUserKeys.getPrivate(), testUser, ADMIN );
        createNode( testUser, testUserPrivateKey );

        removeUser( defaultUserKeys.getPrivate(), testUser );

        try
        {
            createNode( testUser, testUserPrivateKey );
            fail( "Should not be possible to create node using removed user" );
        }
        catch ( Exception e )
        {
            // expected
        }

        createNode( DEFAULT_USER, defaultUserKeys.getPrivate() );
    }

    @Test
    public void createdReaderUserNotAbleToWrite() throws Throwable
    {
        KeyPair defaultUserKeys = generateKeyPair();

        restartWithAuthEnabled( defaultUserKeys.getPublic() );

        createNode( DEFAULT_USER, defaultUserKeys.getPrivate() );

        String testUser = "testUser";
        PrivateKey testUserPrivateKey = addNewUser( defaultUserKeys.getPrivate(), testUser, READER );
        readNodes( testUser, testUserPrivateKey );

        try
        {
            createNode( testUser, testUserPrivateKey );
            fail( "Should not be possible to create node using reader user" );
        }
        catch ( Exception e )
        {
            // expected
        }
    }

    private static PrivateKey addNewUser( PrivateKey defaultUserPrivateKey, String username, String... roles )
    {
        KeyPair newUserKeyPair = generateKeyPair();

        AuthToken authToken = pkiAuthToken( DEFAULT_USER, defaultUserPrivateKey );
        try ( Driver driver = GraphDatabase.driver( "bolt://localhost", authToken );
                Session session = driver.session() )
        {
            String query = "CALL addPkiUser({username}, {key}, {roles})";

            Map<String,Object> params = new HashMap<>();
            params.put( "username", username );
            params.put( "key", publicKeyAsString( newUserKeyPair.getPublic() ) );
            params.put( "roles", roles );

            session.run( query, params ).consume();
        }

        return newUserKeyPair.getPrivate();
    }

    private static void removeUser( PrivateKey defaultUserPrivateKey, String username )
    {
        AuthToken authToken = pkiAuthToken( DEFAULT_USER, defaultUserPrivateKey );
        try ( Driver driver = GraphDatabase.driver( "bolt://localhost", authToken );
                Session session = driver.session() )
        {
            String query = "call removePkiUser({username})";
            Map<String,Object> params = singletonMap( "username", username );

            session.run( query, params ).consume();
        }
    }

    private static void createNode( String username, PrivateKey privateKey )
    {
        AuthToken authToken = pkiAuthToken( username, privateKey );

        try ( Driver driver = GraphDatabase.driver( "bolt://localhost", authToken );
                Session session = driver.session() )
        {
            String nodeName = UUID.randomUUID().toString();
            session.run( "CREATE ({name: '" + nodeName + "'})" ).consume();
            Value value = session.run( "MATCH (n {name: '" + nodeName + "'}) RETURN count(n)" ).single().get( 0 );
            assertThat( value.asLong(), equalTo( 1L ) );
        }
    }

    private static void readNodes( String username, PrivateKey privateKey )
    {
        AuthToken authToken = pkiAuthToken( username, privateKey );

        try ( Driver driver = GraphDatabase.driver( "bolt://localhost", authToken );
                Session session = driver.session() )
        {
            Value value = session.run( "MATCH (n) RETURN count(n)" ).single().get( 0 );
            assertThat( value.asLong(), greaterThanOrEqualTo( 1L ) );
        }
    }

    private static AuthToken pkiAuthToken( String username, PrivateKey privateKey )
    {
        String encryptedUsername = encrypt( privateKey, username );
        Map<String,Object> authParams = singletonMap( ENCRYPTED_USERNAME_PARAMETER_NAME, encryptedUsername );
        return AuthTokens.custom( username, "", "", "", authParams );
    }

    private static KeyPair generateKeyPair()
    {
        try
        {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( CRYPTO_ALGORITHM );
            keyPairGenerator.initialize( 2048 );
            return keyPairGenerator.generateKeyPair();
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new RuntimeException( e );
        }
    }

    private static String encrypt( PrivateKey privateKey, String text )
    {
        try
        {
            Cipher rsa = Cipher.getInstance( CRYPTO_ALGORITHM );
            rsa.init( Cipher.ENCRYPT_MODE, privateKey );
            byte[] bytes = rsa.doFinal( text.getBytes( UTF_8 ) );
            return Base64.getEncoder().encodeToString( bytes );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }
    }

    private void restartWithAuthEnabled( PublicKey defaultUserPublicKey ) throws Exception
    {
        neo4j.restart( Neo4jSettings.TEST_SETTINGS.updateWith( Neo4jSettings.AUTH_ENABLED, "true" )
                .updateWith( PkiAuthPlugin.DEFAULT_USER_PUBLIC_KEY_SETTING, publicKeyAsString( defaultUserPublicKey ) )
                .updateWith( "dbms.security.auth_provider", "plugin-org.neo4j.example.auth.plugin.pki.PkiAuthPlugin" ) );
    }

    private static String publicKeyAsString( PublicKey publicKey )
    {
        return Base64.getEncoder().encodeToString( publicKey.getEncoded() );
    }
}
