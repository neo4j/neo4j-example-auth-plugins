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
package org.neo4j.example.auth.plugin.ldap;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.neo4j.server.security.enterprise.auth.plugin.api.AuthToken;
import org.neo4j.server.security.enterprise.auth.plugin.api.AuthenticationException;
import org.neo4j.server.security.enterprise.auth.plugin.api.PredefinedRoles;
import org.neo4j.server.security.enterprise.auth.plugin.api.RealmOperations;
import org.neo4j.server.security.enterprise.auth.plugin.spi.AuthInfo;
import org.neo4j.server.security.enterprise.auth.plugin.spi.AuthPlugin;

/**
 * This example shows how you could authorize against an LDAP server that has a different schema configuration
 * for how a user's group membership is specified than the Neo4j built-in `ldap` auth provider would currently support.
 *
 * Instead of having the groups that the user is a member of listed on an attribute on the user, the
 * targeted LDAP server has the users that are member of a group listed on an attribute on the group.
 *
 * The plugin uses JNDI to authenticate and authorize users against an LDAP server
 * (using the `simple` username / password authentication mechanism), and then performs
 * an authorization search for groups where the `memberUid` attribute includes the user.
 * It then uses a naive static group-to-role mapping method `getNeo4jRoleForGroupId` to get the associated roles.
 */
public class LdapGroupHasUsersAuthPlugin extends AuthPlugin.Adapter
{
    public static final String PLUGIN_NAME = "ldap-alternative-groups";
    public static final String LDAP_SERVER_URL_SETTING = "dbms.security.ldap.host";

    private static final String GROUP_SEARCH_BASE = "ou=groups,dc=example,dc=com";
    private static final String GROUP_SEARCH_FILTER = "(&(objectClass=posixGroup)(memberUid={0}))";
    private static final String GROUP_ID = "gidNumber";

    private RealmOperations api;
    private String ldapServerUrl;

    @Override
    public String name()
    {
        return PLUGIN_NAME;
    }

    @Override
    public void initialize( RealmOperations realmOperations ) throws Exception
    {
        api = realmOperations;
        api.log().info( "initialized!" );

        Path neo4jConf = api.neo4jHome().resolve( "conf/neo4j.conf" );

        Properties properties = new Properties();
        try ( BufferedReader reader = Files.newBufferedReader( neo4jConf ) )
        {
            properties.load( reader );
        }

        ldapServerUrl = (String) properties.get( LDAP_SERVER_URL_SETTING );
        if ( ldapServerUrl == null )
        {
            throw new IllegalStateException( "Missing ldap server url setting '" + LDAP_SERVER_URL_SETTING + "'." );
        }
    }

    @Override
    public AuthInfo authenticateAndAuthorize( AuthToken authToken ) throws AuthenticationException
    {
        try
        {
            String username = authToken.principal();
            char[] password = authToken.credentials();

            api.log().info( "Log in attempted for user '" + username + "'.");

            LdapContext ctx = authenticate( username, password );

            api.log().info( "User '" + username + "' authenticated." );

            Set<String> roles = authorize( ctx, username );

            api.log().info( "User '" + username + "' authorized roles " + roles );

            return AuthInfo.of( username, roles );
        }
        catch ( NamingException e )
        {
            throw new AuthenticationException( e.getMessage() );
        }
    }

    private LdapContext authenticate( String username, char[] password ) throws NamingException
    {
        Hashtable<String,Object> env = new Hashtable<>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( Context.PROVIDER_URL, ldapServerUrl );
        env.put( Context.SECURITY_PRINCIPAL, String.format( "cn=%s,ou=users,dc=example,dc=com", username ) );
        env.put( Context.SECURITY_CREDENTIALS, password );

        return new InitialLdapContext( env, null );
    }

    private Set<String> authorize( LdapContext ctx, String username ) throws NamingException
    {
        Set<String> roleNames = new LinkedHashSet<>();

        // Setup our search controls
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        searchCtls.setReturningAttributes( new String[]{GROUP_ID} );

        // Use a search argument to prevent potential code injection
        Object[] searchArguments = new Object[]{username};

        // Search for groups that has the user as a member
        NamingEnumeration result = ctx.search( GROUP_SEARCH_BASE, GROUP_SEARCH_FILTER, searchArguments, searchCtls );

        if ( result.hasMoreElements() )
        {
            SearchResult searchResult = (SearchResult) result.next();

            Attributes attributes = searchResult.getAttributes();
            if ( attributes != null )
            {
                NamingEnumeration attributeEnumeration = attributes.getAll();
                while ( attributeEnumeration.hasMore() )
                {
                    Attribute attribute = (Attribute) attributeEnumeration.next();
                    String attributeId = attribute.getID();
                    if ( attributeId.equalsIgnoreCase( GROUP_ID ) )
                    {
                        // We found a group that the user is a member of. See if it has a role mapped to it
                        String groupId = (String) attribute.get();
                        String neo4jGroup = getNeo4jRoleForGroupId( groupId );
                        if ( neo4jGroup != null )
                        {
                            // Yay! Add it to our set of roles
                            roleNames.add( neo4jGroup );
                        }
                    }
                }
            }
        }
        return roleNames;
    }

    private static String getNeo4jRoleForGroupId( String groupId )
    {
        if ( "500".equals( groupId ) )
            return PredefinedRoles.READER;
        if ( "501".equals( groupId ) )
            return PredefinedRoles.PUBLISHER;
        if ( "502".equals( groupId ) )
            return PredefinedRoles.ARCHITECT;
        if ( "503".equals( groupId ) )
            return PredefinedRoles.ADMIN;
        return null;
    }
}
