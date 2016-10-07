package org.rcredits.pos;

import android.database.Cursor;

/**
 * Created by William on 7/8/14.
 */
public class Q {
    private Cursor q;

    Q (Cursor cursor) {q = cursor;}

    public long rowid() {return q.getLong(0);}
    public int col(String name) {return q.getColumnIndex(name);}
    public int getInt(String name) {return q.getInt(col(name));}
    public long getLong(String name) {return q.getLong(col(name));}
    public byte[] getBlob(String name) {return q.getBlob(col(name));}
    public String getString(String name) {return q.getString(col(name));}
    public String getString(int col) {return q.getString(col);}
    public Double getDouble(String name) {return q.getDouble(col(name));}
    public Double getDouble(int col) {return q.getDouble(col);}
    public boolean moveToFirst() {return q.moveToFirst();}
    public boolean moveToNext() {return q.moveToNext();}
    public void close() {if (q != null) q.close();}
    public boolean invalid() {return (q == null);}
    public boolean isAgent() {return (getInt(DbSetup.AGT_FLAG) == A.TX_AGENT);}
}
