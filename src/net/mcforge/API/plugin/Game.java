/*******************************************************************************
* Copyright (c) 2012 MCForge.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Public License v3.0
* which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/gpl.html
******************************************************************************/
package net.mcforge.API.plugin;

import net.mcforge.server.Server;
import net.mcforge.server.Tick;

import java.util.Properties;

public abstract class Game extends Plugin implements Tick {

    public Game(Server server, Properties properties) {
        super(server, properties);
    }
    public Game(Server server) {
        super(server);
    }
    
    public abstract void tick();
}

