package org.neo4j.example.auth.plugin;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Set;

public class UserInfo
{
    private final PublicKey publicKey;
    private final Set<String> roles;

    public UserInfo( PublicKey publicKey, Set<String> roles )
    {
        this.publicKey = publicKey;
        this.roles = roles;
    }

    public PublicKey getPublicKey()
    {
        return publicKey;
    }

    public Set<String> getRoles()
    {
        return Collections.unmodifiableSet( roles );
    }
}
