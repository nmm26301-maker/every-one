import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.writer.pool.DexPool;
public final class DexJobSlice {
 public static void main(String[] args)throws Exception {
  DexFile dex=DexFileFactory.loadDexFile(args[0],Opcodes.getDefault());List<ClassDef> keep=new ArrayList<>();int removed=0;
  for(ClassDef c:dex.getClasses()){
   if(c.getType().equals("Lorg/openenglish/reader/CloudClient$Job;")||c.getType().startsWith("Lorg/openenglish/reader/CloudClient$Job$"))removed++;else keep.add(c);
  }
  if(removed!=1)throw new IllegalStateException("Unexpected baseline Job class set");
  DexPool.writeTo(args[1],new ImmutableDexFile(dex.getOpcodes(),keep));System.out.println("Preserved "+keep.size()+" original classes");
 }
}
