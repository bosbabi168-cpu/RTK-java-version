package org.rtk.map.script;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;

/**
 * Port of typel_inst (map/sl.c): a Lua userdata wrapping a Java game object.
 *
 * Besides the Java object itself ({@code self}), every instance carries a
 * private Lua data table, used as the last lookup stop by the shared
 * metatable's __index (mirroring inst->dataref in the C code).
 */
public class ScriptInstance extends LuaUserdata {

    public final ScriptClass klass;
    public final Object self;
    public final LuaTable data = new LuaTable();

    public ScriptInstance(ScriptClass klass, Object self, LuaValue metatable) {
        super(self, metatable);
        this.klass = klass;
        this.self = self;
    }
}
