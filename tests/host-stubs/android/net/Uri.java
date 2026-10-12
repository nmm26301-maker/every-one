package android.net;
public final class Uri {public final String path;private Uri(String p){path=p;}public static Uri parse(String p){return new Uri(p);}}
