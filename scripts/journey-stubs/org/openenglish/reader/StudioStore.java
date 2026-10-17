package org.openenglish.reader;
import android.content.Context;import android.database.sqlite.*;
public class StudioStore extends SQLiteOpenHelper{public StudioStore(Context c){super(c,"unused",null,1);}public void onCreate(SQLiteDatabase d){}public void onUpgrade(SQLiteDatabase d,int a,int b){}public long addSession(String title,String scene){return 0;}}
