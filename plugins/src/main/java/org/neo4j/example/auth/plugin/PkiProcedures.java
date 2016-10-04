/**
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 * <p>
 * This file is part of Neo4j.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.example.auth.plugin;

import java.util.List;

import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

public class PkiProcedures
{
    @Procedure( name = "addPkiUser", mode = Mode.DBMS )
    public void addPkiUser( @Name( "username" ) String username, @Name( "publicKey" ) String publicKey,
            @Name( "roles" ) List<String> roles )
    {
        PkiRepository.add( username, publicKey, roles.toArray( new String[0] ) );
    }

    @Procedure( name = "removePkiUser", mode = Mode.DBMS )
    public void removePkiUser( @Name( "username" ) String username )
    {
        PkiRepository.remove( username );
    }
}
