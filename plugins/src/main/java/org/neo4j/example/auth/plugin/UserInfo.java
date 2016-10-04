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
