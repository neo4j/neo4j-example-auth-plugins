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
package org.neo4j.example.auth.plugin.pki;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import javax.crypto.Cipher;

import org.neo4j.server.security.enterprise.auth.plugin.api.AuthToken;
import org.neo4j.server.security.enterprise.auth.plugin.api.AuthenticationException;
import org.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles;
import org.neo4j.server.security.enterprise.auth.plugin.api.RealmOperations;
import org.neo4j.server.security.enterprise.auth.plugin.spi.AuthInfo;
import org.neo4j.server.security.enterprise.auth.plugin.spi.AuthPlugin;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PkiAuthPlugin extends AuthPlugin.Adapter
{
    public static final String CRYPTO_ALGORITHM = "RSA";
    public static final String DEFAULT_USER_PUBLIC_KEY_SETTING = "dbms.security.pki.default.public.key";
    public static final String ENCRYPTED_USERNAME_PARAMETER_NAME = "encryptedUsername";
    public static final String DEFAULT_USER = "neo4j";

    @Override
    public void initialize( RealmOperations realmOperations ) throws Exception
    {
        Path neo4jConf = realmOperations.neo4jHome().resolve( "conf/neo4j.conf" );

        Properties properties = new Properties();
        try ( BufferedReader reader = Files.newBufferedReader( neo4jConf ) )
        {
            properties.load( reader );
        }

        String defaultUserPublicKeyString = (String) properties.get( DEFAULT_USER_PUBLIC_KEY_SETTING );
        if ( defaultUserPublicKeyString == null )
        {
            throw new IllegalStateException( "Public key for default user '" + DEFAULT_USER + "' is not set" );
        }

        PkiRepository.add( DEFAULT_USER, defaultUserPublicKeyString, PredefinedRoles.ADMIN );
    }

    @Override
    public AuthInfo authenticateAndAuthorize( AuthToken authToken ) throws AuthenticationException
    {
        String username = authToken.principal();
        Map<String,Object> parameters = authToken.parameters();
        if ( parameters == null )
        {
            return null;
        }

        String base64EncodedEncryptedUsername = (String) parameters.get( ENCRYPTED_USERNAME_PARAMETER_NAME );
        if ( base64EncodedEncryptedUsername == null )
        {
            return null;
        }

        byte[] encryptedUsernameBytes = Base64.getDecoder().decode( base64EncodedEncryptedUsername );

        UserInfo info = PkiRepository.infoFor( username );
        PublicKey publicKey = info.getPublicKey();

        String decryptedUsername = decrypt( publicKey, encryptedUsernameBytes );

        return Objects.equals( username, decryptedUsername ) ? AuthInfo.of( username, info.getRoles() ) : null;
    }

    private static String decrypt( Key decryptionKey, byte[] buffer )
    {
        try
        {
            Cipher rsa = Cipher.getInstance( CRYPTO_ALGORITHM );
            rsa.init( Cipher.DECRYPT_MODE, decryptionKey );
            return new String( rsa.doFinal( buffer ), UTF_8 );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }
    }
}
