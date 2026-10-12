package android.content.res;
import java.io.*;
public class AssetManager {private final File root;public AssetManager(File r){root=r;}public InputStream open(String path)throws IOException{return new FileInputStream(new File(root,path));}}
