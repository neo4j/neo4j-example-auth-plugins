/*
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

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.newSetFromMap;

public class PkiRepository
{
    private static final Map<String,UserInfo> usernameToInfo = new ConcurrentHashMap<>();

    public static void add( String username, String publicKeyString, String... roles )
    {
        PublicKey publicKey = readPublicKey( publicKeyString );
        UserInfo userInfo = new UserInfo( publicKey, newConcurrentSet( roles ) );
        UserInfo current = usernameToInfo.putIfAbsent( username, userInfo );
        if ( current != null )
        {
            throw new IllegalArgumentException( "User: '" + username + "' is already in the repository" );
        }
    }

    public static void remove( String username )
    {
        usernameToInfo.remove( username );
    }

    public static UserInfo infoFor( String username )
    {
        return getUserInfo( username );
    }

    private static UserInfo getUserInfo( String username )
    {
        UserInfo info = usernameToInfo.get( username );
        if ( info == null )
        {
            throw new IllegalArgumentException( "User: '" + username + "' is not in the repository" );
        }
        return info;
    }

    private static PublicKey readPublicKey( String publicKeyString )
    {
        try
        {
            byte[] bytes = Base64.getDecoder().decode( publicKeyString );
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec( bytes );
            KeyFactory keyFactory = KeyFactory.getInstance( PkiAuthPlugin.CRYPTO_ALGORITHM );
            return keyFactory.generatePublic( keySpec );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }
    }

    @SafeVarargs
    private static <T> Set<T> newConcurrentSet( T... initialElements )
    {
        Set<T> set = newSetFromMap( new ConcurrentHashMap<>() );
        Collections.addAll( set, initialElements );
        return set;
    }

    /**
     * Used to reset the repository during tests
     */
    public static void reset()
    {
        usernameToInfo.clear();
    }
}
