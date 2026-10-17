import java.util.*;
import org.jf.dexlib2.*;import org.jf.dexlib2.iface.*;import org.jf.dexlib2.iface.instruction.*;import org.jf.dexlib2.iface.reference.*;import org.jf.dexlib2.immutable.*;import org.jf.dexlib2.writer.pool.DexPool;
/** Add the isolated journey namespace, verifying compile-only baseline contracts. */
public final class DexJourneyMerge {
 static final String PREFIX="Lorg/openenglish/reader/Journey";
 static Map<String,ClassDef> types=new HashMap<>();
 static boolean matches(MethodReference a,MethodReference b){return a.getName().equals(b.getName())&&a.getReturnType().equals(b.getReturnType())&&a.getParameterTypes().toString().equals(b.getParameterTypes().toString());}
 static boolean resolves(MethodReference r,String type){
  if(type==null)return false;if(!type.startsWith("Lorg/openenglish/reader/"))return true;
  ClassDef c=types.get(type);if(c==null)return false;for(Method m:c.getMethods())if(matches(m,r))return true;
  if(resolves(r,c.getSuperclass()))return true;for(String i:c.getInterfaces())if(resolves(r,i))return true;return false;
 }
 public static void main(String[] a)throws Exception{
  DexFile base=DexFileFactory.loadDexFile(a[0],Opcodes.getDefault()),patch=DexFileFactory.loadDexFile(a[1],base.getOpcodes());
  List<ClassDef> merged=new ArrayList<>();for(ClassDef c:base.getClasses()){types.put(c.getType(),c);merged.add(c);}
  for(ClassDef c:patch.getClasses()){if(!c.getType().startsWith(PREFIX)||types.containsKey(c.getType()))throw new IllegalStateException("Unexpected or colliding class "+c.getType());types.put(c.getType(),c);merged.add(c);}
  int contracts=0;
  for(ClassDef c:patch.getClasses())for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof MethodReference){MethodReference mr=(MethodReference)r;if(mr.getDefiningClass().startsWith("Lorg/openenglish/reader/")&&!mr.getDefiningClass().startsWith(PREFIX)){// All direct baseline methods must really exist; constructors never inherit.
    ClassDef target=types.get(mr.getDefiningClass());boolean found=false;if(target!=null)for(Method candidate:target.getMethods())if(matches(candidate,mr))found=true;
    if(!found&&!mr.getName().equals("<init>")){String parent=target==null?null:target.getSuperclass();found=parent!=null&&!parent.equals("Ljava/lang/Object;")&&resolves(mr,parent);}
    if(!found)throw new IllegalStateException("Missing baseline contract "+mr);contracts++;
  }}}
  DexPool.writeTo(a[2],new ImmutableDexFile(base.getOpcodes(),merged));System.out.println("Preserved "+base.getClasses().size()+" classes; added "+patch.getClasses().size()+"; verified "+contracts+" baseline method uses");
 }
}
