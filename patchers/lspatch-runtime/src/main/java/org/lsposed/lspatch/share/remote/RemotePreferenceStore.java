package org.lsposed.lspatch.share.remote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A module's remote preferences, stored the way Vector's daemon stores them.
 *
 * <p>This is a direct port of {@code org.matrix.vector.daemon.data.PreferenceStore}: one SQLite
 * {@code configs} table keyed by {@code (module_pkg_name, user_id, group, key)} with a per-key
 * serialized blob, and a diff that {@link #updateModulePrefs applies} as replace-or-delete. Keeping
 * the daemon's schema means the semantics a module relies on — value types preserved across the
 * binder, including {@code Set<String>} — are exactly the ones Vector guarantees.</p>
 *
 * <p>The only divergence from the daemon is <i>where</i> the database lives: this opens it through a
 * {@link Context}, so the same class serves the manager (the manager app's database directory, the
 * persistent store shared by a companion app and every host) and an embedded host (the host app's own
 * directory). Nothing about the schema or the logic differs between the two.</p>
 */
public class RemotePreferenceStore {

    private static final String TAG = "LSPatch-RemotePrefs";
    private static final String DB_NAME = "lspatch-xposed-remote.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "configs";

    private final Helper helper;

    public RemotePreferenceStore(Context context) {
        // The context is used directly, not via getApplicationContext(): this is built during app
        // bootstrap (LocalApplicationService), before the Application exists, so getApplicationContext()
        // returns null there. The helper opens the database lazily, so construction touches no disk.
        this.helper = new Helper(context);
    }

    /**
     * Every stored value of a {@code (module, user, group)}, deserialized. The payload the client
     * turns back into a {@link android.content.SharedPreferences} snapshot.
     */
    public Map<String, Object> getModulePrefs(String packageName, int userId, String group) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Cursor cursor = helper.getReadableDatabase().query(
                TABLE, new String[]{"`key`", "data"},
                "module_pkg_name = ? AND user_id = ? AND `group` = ?",
                new String[]{packageName, Integer.toString(userId), group},
                null, null, null)) {
            while (cursor.moveToNext()) {
                Object value = deserialize(cursor.getBlob(1));
                if (value != null) result.put(cursor.getString(0), value);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getModulePrefs " + packageName + "/" + group, t);
        }
        return result;
    }

    /**
     * Applies a diff key by key: a {@link Serializable} value replaces the row, anything else (a
     * {@code null}, or a value that will not serialize) deletes it. This is what the client's
     * {@code delete}/{@code put} sets turn into once the group-level {@code clear} has been handled by
     * the caller.
     */
    public void updateModulePrefs(String packageName, int userId, String group, Map<String, Object> diff) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<String, Object> entry : diff.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Serializable) {
                    ContentValues values = new ContentValues();
                    values.put("module_pkg_name", packageName);
                    values.put("user_id", userId);
                    values.put("`group`", group);
                    values.put("`key`", entry.getKey());
                    values.put("data", serialize((Serializable) value));
                    db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                } else {
                    db.delete(TABLE,
                            "module_pkg_name = ? AND user_id = ? AND `group` = ? AND `key` = ?",
                            new String[]{packageName, Integer.toString(userId), group, entry.getKey()});
                }
            }
            db.setTransactionSuccessful();
        } catch (Throwable t) {
            Log.w(TAG, "updateModulePrefs " + packageName + "/" + group, t);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes stored preferences. A null {@code userId} or {@code group} widens the delete to every
     * user, or every group, of the module — mirroring the daemon's {@code deleteModulePrefs}.
     */
    public void deleteModulePrefs(String packageName, Integer userId, String group) {
        StringBuilder where = new StringBuilder("module_pkg_name = ?");
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(packageName);
        if (userId != null) {
            where.append(" AND user_id = ?");
            args.add(Integer.toString(userId));
        }
        if (group != null) {
            where.append(" AND `group` = ?");
            args.add(group);
        }
        try {
            helper.getWritableDatabase().delete(TABLE, where.toString(), args.toArray(new String[0]));
        } catch (Throwable t) {
            Log.w(TAG, "deleteModulePrefs " + packageName, t);
        }
    }

    private static byte[] serialize(Serializable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (Throwable t) {
            Log.w(TAG, "serialize", t);
            return null;
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] blob) {
        if (blob == null) return null;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(blob))) {
            return in.readObject();
        } catch (Throwable t) {
            // A value written by a module class this process cannot resolve, or a corrupt blob:
            // drop it rather than fail the whole group read.
            Log.w(TAG, "deserialize", t);
            return null;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "module_pkg_name TEXT NOT NULL,"
                    + "user_id INTEGER NOT NULL,"
                    + "`group` TEXT NOT NULL,"
                    + "`key` TEXT NOT NULL,"
                    + "data BLOB,"
                    + "PRIMARY KEY (module_pkg_name, user_id, `group`, `key`))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // v1 only so far; future migrations go here.
        }
    }
}
