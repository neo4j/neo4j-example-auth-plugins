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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.example.auth.plugin.pki.PkiAuthPlugin;
import org.neo4j.example.auth.plugin.pki.PkiProcedures;
import org.neo4j.example.auth.plugin.pki.PkiRepository;
import org.neo4j.internal.helpers.HostnamePort;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.rule.TestDirectory;

import static com.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles.ADMIN;
import static com.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles.READER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.fail;
import static org.neo4j.configuration.connectors.BoltConnector.DEFAULT_PORT;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.CRYPTO_ALGORITHM;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.DEFAULT_USER;
import static org.neo4j.example.auth.plugin.pki.PkiAuthPlugin.ENCRYPTED_USERNAME_PARAMETER_NAME;

public class PkiAuthPluginIT
{
    @Rule
    public final TestDirectory testDirectory = TestDirectory.testDirectory();

    private static final Config config = Config.builder().withLogging( Logging.none() ).withoutEncryption().build();

    private DatabaseManagementService databases;
    private KeyPair defaultUserKeys;
    private ConnectorPortRegister connectorPortRegister;

    @Before
    public void setUp() throws Exception
    {
        defaultUserKeys = generateKeyPair();

        Neo4jLayout home = Neo4jLayout.of( testDirectory.homeDir() );

        // Create directories and write out test config file
        File configDir = new File( home.homeDirectory(), "conf" );;
        configDir.mkdirs();

        try ( FileWriter fileWriter = new FileWriter( new File( configDir, "pki.conf" ) ) )
        {
            fileWriter.write( PkiAuthPlugin.DEFAULT_USER_PUBLIC_KEY_SETTING + "=" +
                              publicKeyAsString( defaultUserKeys.getPublic() ) );
        }

        // Start up server with authentication enabled
        databases = new TestEnterpriseDatabaseManagementServiceBuilder( home )
                .setConfig( GraphDatabaseSettings.auth_enabled, true )
                .setConfig( SecuritySettings.authentication_providers, List.of( "plugin-org.neo4j.example.auth.plugin.pki.PkiAuthPlugin" ) )
                .setConfig( SecuritySettings.authorization_providers, List.of( "plugin-org.neo4j.example.auth.plugin.pki.PkiAuthPlugin" ) )
                .setConfig( BoltConnector.enabled, true )
                .setConfig( BoltConnector.listen_address, new SocketAddress( "localhost", DEFAULT_PORT ) )
                .build();
        GraphDatabaseAPI db = (GraphDatabaseAPI) databases.database( GraphDatabaseSettings.DEFAULT_DATABASE_NAME );
        db.getDependencyResolver().resolveDependency( GlobalProcedures.class ).registerProcedure( PkiProcedures.class );
        connectorPortRegister = db.getDependencyResolver().resolveDependency( ConnectorPortRegister.class );
    }

    private URI boltURI()
    {
        HostnamePort hostPort = connectorPortRegister.getLocalAddress( BoltConnector.NAME );
        return URI.create( "bolt" + "://" + hostPort + "/" );
    }

    @After
    public void tearDown()
    {
        PkiRepository.reset();
        databases.shutdown();
    }

    @Test
    public void authenticateDefaultUser()
    {
        createNode( DEFAULT_USER, defaultUserKeys.getPrivate() );
    }

    @Test
    public void addUserPerformOperationAndRemoveUser()
    {
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
    public void createdReaderUserNotAbleToWrite()
    {
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

    private PrivateKey addNewUser( PrivateKey defaultUserPrivateKey, String username, String... roles )
    {
        KeyPair newUserKeyPair = generateKeyPair();

        AuthToken authToken = pkiAuthToken( DEFAULT_USER, defaultUserPrivateKey );
        try ( Driver driver = GraphDatabase.driver( boltURI(), authToken, config );
                Session session = driver.session() )
        {
            String query = "CALL addPkiUser($username, $key, $roles)";

            Map<String,Object> params = new HashMap<>();
            params.put( "username", username );
            params.put( "key", publicKeyAsString( newUserKeyPair.getPublic() ) );
            params.put( "roles", roles );

            session.run( query, params ).consume();
        }

        return newUserKeyPair.getPrivate();
    }

    private void removeUser( PrivateKey defaultUserPrivateKey, String username )
    {
        AuthToken authToken = pkiAuthToken( DEFAULT_USER, defaultUserPrivateKey );
        try ( Driver driver = GraphDatabase.driver( boltURI(), authToken, config );
                Session session = driver.session() )
        {
            String query = "call removePkiUser($username)";
            Map<String,Object> params = singletonMap( "username", username );

            session.run( query, params ).consume();
        }
    }

    private void createNode( String username, PrivateKey privateKey )
    {
        AuthToken authToken = pkiAuthToken( username, privateKey );

        try ( Driver driver = GraphDatabase.driver( boltURI(), authToken, config );
                Session session = driver.session() )
        {
            String nodeName = UUID.randomUUID().toString();
            session.run( "CREATE ({name: '" + nodeName + "'})" ).consume();
            Value value = session.run( "MATCH (n {name: '" + nodeName + "'}) RETURN count(n)" ).single().get( 0 );
            assertThat( value.asLong(), equalTo( 1L ) );
        }
    }

    private void readNodes( String username, PrivateKey privateKey )
    {
        AuthToken authToken = pkiAuthToken( username, privateKey );

        try ( Driver driver = GraphDatabase.driver( boltURI(), authToken, config );
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

    private static String publicKeyAsString( PublicKey publicKey )
    {
        return Base64.getEncoder().encodeToString( publicKey.getEncoded() );
    }
}
