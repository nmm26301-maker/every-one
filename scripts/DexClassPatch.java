import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.writer.pool.DexPool;

/** Replace one verified class while preserving the remaining compiled snapshot. */
public final class DexClassPatch {
 public static void main(String[] args) throws Exception {
  if(args.length!=4)throw new IllegalArgumentException("base.dex replacement.dex output.dex class-descriptor");
  DexFile base=DexFileFactory.loadDexFile(args[0],Opcodes.getDefault());
  DexFile patch=DexFileFactory.loadDexFile(args[1],base.getOpcodes());
  if(patch.getClasses().size()!=1)throw new IllegalStateException("Replacement must contain exactly one class");
  ClassDef replacement=patch.getClasses().iterator().next();
  if(!replacement.getType().equals(args[3]))throw new IllegalStateException("Unexpected replacement descriptor");
  List<ClassDef> classes=new ArrayList<>();int removed=0;
  for(ClassDef c:base.getClasses()){
   if(c.getType().equals(args[3]))removed++;else classes.add(c);
  }
  if(removed!=1)throw new IllegalStateException("Baseline must contain exactly one target class");
  classes.add(replacement);
  DexPool.writeTo(args[2],new ImmutableDexFile(base.getOpcodes(),classes));
  System.out.println("Replaced "+args[3]+"; preserved "+(classes.size()-1)+" classes");
 }
}
