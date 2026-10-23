package android.content;
import android.content.res.AssetManager;import java.io.File;
/** Host filesystem fixture; does not emulate Android storage durability. */
public final class Context {
 private final File files,assets;
 public Context(File files,File assets){this.files=files;this.assets=assets;}
 public File getNoBackupFilesDir(){return files;}
 public AssetManager getAssets(){return new AssetManager(assets);}
}
