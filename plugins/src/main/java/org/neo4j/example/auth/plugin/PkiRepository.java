package org.neo4j.example.auth.plugin;

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
}
