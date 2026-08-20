package org.rtk.map.script;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Port of typel_class (map/sl.c): a scriptable game type such as Player,
 * NPC, Mob, Item or Registry.
 *
 * Attribute reads resolve in the same order as typel_mtindex in C:
 *   1. the class getter (playerl_getattr, regl_getattr, ...)
 *   2. the prototype table (methods added via typel_extendproto)
 *   3. the instance's private data table
 * Writes go through the class setter first, then fall back to the
 * instance data table.
 */
public class ScriptClass {

    /** getattr: return a value for the attribute, or null when not handled. */
    public interface Getter {
        LuaValue get(Object self, String attr);
    }

    /** setattr: store the attribute, returning false when not handled. */
    public interface Setter {
        boolean set(Object self, String attr, LuaValue value);
    }

    /** A prototype method bound to the instance's Java object. */
    public interface Method {
        Varargs call(Object self, Varargs args);
    }

    /** Constructor invoked by calling the class table from Lua, e.g. NPC(id). */
    public interface Ctor {
        Object create(Varargs args);
    }

    public final String name;
    public final LuaTable proto = new LuaTable();
    public Getter getter;
    public Setter setter;
    public Ctor ctor;

    public ScriptClass(String name) {
        this.name = name;
    }

    /** typel_extendproto(): expose a method on this class's prototype. */
    public void addMethod(String methodName, Method method) {
        proto.set(methodName, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue selfArg = args.arg1();
                if (!(selfArg instanceof ScriptInstance)) {
                    return NIL;
                }
                ScriptInstance inst = (ScriptInstance) selfArg;
                if (inst.self == null) {
                    return NIL;
                }
                return method.call(inst.self, args);
            }
        });
    }
}
