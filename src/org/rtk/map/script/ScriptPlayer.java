package org.rtk.map.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;

/**
 * Minimal player model seen by the scripting layer.
 *
 * This is the script-facing slice of struct USER / mmo_charstatus. Right now
 * it is backed by in-memory state so the scripting subsystem can be developed
 * and tested ahead of the full gameplay port; when pc.c lands, the fields
 * and registries move onto the real player object and persist through
 * char-server saves (Registry / RegistryString / NPCRegistry / QuestRegistry
 * tables, see CharDb).
 */
public class ScriptPlayer {

    public int id;
    public String name = "";
    public int level = 1;
    public int sex;
    public int path;      // class/path id
    public int gmLevel;
    public int m, x, y;   // current map position

    // pc_readglobalreg / pc_setglobalreg etc.
    public final Map<String, Integer> registry = new HashMap<>();
    public final Map<String, String> registryString = new HashMap<>();
    public final Map<String, Integer> npcInt = new HashMap<>();
    public final Map<String, Integer> questReg = new HashMap<>();

    // simple inventory for the early item bindings
    public final Map<String, Integer> items = new HashMap<>();

    /** sd->coref: the coroutine driving this player's current interaction. */
    public LuaThread coroutine;

    /** What the script is currently waiting on (dialog/menu/input). */
    public PendingDialog pendingDialog;

    /** Everything the scripts "sent" to this player (minitext, dialogs...). */
    public final List<String> outbox = new ArrayList<>();

    /** Cached userdata so scripts always see the same object identity. */
    LuaValue udata;
    LuaValue registryUdata;
    LuaValue registryStringUdata;
    LuaValue npcIntUdata;
    LuaValue questUdata;

    public ScriptPlayer(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Description of a blocking call a script yielded on. */
    public static class PendingDialog {
        public final String kind;    // "menuString", "dialogSeq", "menuSeq", "inputString", "inputNumber"
        public final String message;
        public final List<String> options;

        public PendingDialog(String kind, String message, List<String> options) {
            this.kind = kind;
            this.message = message;
            this.options = options;
        }

        @Override
        public String toString() {
            return kind + "(" + message + (options != null ? " " + options : "") + ")";
        }
    }
}
