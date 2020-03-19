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
package org.neo4j.example.auth.plugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import com.neo4j.server.security.enterprise.auth.plugin.api.AuthProviderOperations;
import com.neo4j.server.security.enterprise.auth.plugin.api.AuthToken;
import com.neo4j.server.security.enterprise.auth.plugin.api.AuthenticationException;
import com.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles;
import com.neo4j.server.security.enterprise.auth.plugin.spi.AuthInfo;
import com.neo4j.server.security.enterprise.auth.plugin.spi.AuthPlugin;

public class MyAuthPlugin extends AuthPlugin.Adapter
{
    private AuthProviderOperations api;

    @Override
    public AuthInfo authenticateAndAuthorize( AuthToken authToken ) throws AuthenticationException
    {
        String username = authToken.principal();
        char[] password = authToken.credentials();

        api.log().info( "Log in attempted for user '" + username + "'.");

        if ( username != null && password != null )
        {
            if ( username.equals( "moraeus" ) && Arrays.equals( password, "suearom".toCharArray() ) )
            {
                api.log().info( "Successful log in. Welcome Kalle Moraeus! You are granted admin permissions." );
                return AuthInfo.of( "moraeus", Collections.singleton( PredefinedRoles.ADMIN ) );
            }
            else if ( username.equals( "neo4j" ) && Arrays.equals( password, "neo4j".toCharArray() ) )
            {
                api.log().info( "Successful log in. You are granted reader permissions." );
                return AuthInfo.of( "neo4j", Collections.singleton( PredefinedRoles.READER ) );
            }
        }
        return null;
    }

    @Override
    public void initialize( AuthProviderOperations authProviderOperations )
    {
        api = authProviderOperations;
        api.log().info( "initialized!" );

        loadConfig();
    }

    private void loadConfig()
    {
        Path configFile = resolveConfigFilePath();
        Properties properties = loadProperties( configFile );

        String myProperty = properties.getProperty( "my.auth.property" );
        api.log().info( "my.auth.property=" + myProperty );
    }

    private Path resolveConfigFilePath()
    {
        return api.neo4jHome().resolve( "conf/MyAuthPlugin.conf" );
    }

    private Properties loadProperties( Path configFile )
    {
        Properties properties = new Properties();

        try
        {
            InputStream inputStream = new FileInputStream( configFile.toFile() );
            properties.load( inputStream );
        }
        catch ( IOException e )
        {
            api.log().error( "Failed to load config file '" + configFile.toString() + "'." );
        }
        return properties;
    }
}
