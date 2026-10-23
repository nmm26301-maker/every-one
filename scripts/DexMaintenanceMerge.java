import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.writer.pool.DexPool;

/** Replace only the three reviewed maintenance class families. */
public final class DexMaintenanceMerge {
 static final String OWNER="Lorg/openenglish/reader/";
 static final String[] ROOTS={"JourneyActivity","JourneyPhoneEngine","JourneyState"};
 static final Map<String,ClassDef> types=new HashMap<>();
 static boolean selected(String type){for(String root:ROOTS)if(type.equals(OWNER+root+";")||type.startsWith(OWNER+root+"$"))return true;return false;}
 static boolean matches(MethodReference a,MethodReference b){return a.getName().equals(b.getName())&&a.getReturnType().equals(b.getReturnType())&&a.getParameterTypes().toString().equals(b.getParameterTypes().toString());}
 static boolean resolves(MethodReference method,String type,Set<String> seen){
  if(type==null||!seen.add(type))return false;
  if(!type.startsWith(OWNER)&&!type.startsWith("Lai/onnxruntime/"))return !method.getName().equals("<init>");
  ClassDef target=types.get(type);if(target==null)return false;
  for(Method candidate:target.getMethods())if(matches(candidate,method))return true;
  if(method.getName().equals("<init>"))return false;
  if(resolves(method,target.getSuperclass(),seen))return true;
  for(String parent:target.getInterfaces())if(resolves(method,parent,seen))return true;
  return false;
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("base.dex patch.dex merged.dex");
  DexFile base=DexFileFactory.loadDexFile(args[0],Opcodes.getDefault()),patch=DexFileFactory.loadDexFile(args[1],base.getOpcodes());
  List<ClassDef> merged=new ArrayList<>(),replacements=new ArrayList<>();Set<String> roots=new HashSet<>();int removed=0,contracts=0;
  for(ClassDef c:base.getClasses()){if(types.put(c.getType(),c)!=null)throw new IllegalStateException("Duplicate base class");if(selected(c.getType()))removed++;else merged.add(c);}
  for(String root:ROOTS)if(!types.containsKey(OWNER+root+";"))throw new IllegalStateException("Missing maintenance baseline root "+root);
  types.keySet().removeIf(DexMaintenanceMerge::selected);
  for(ClassDef c:patch.getClasses())if(selected(c.getType())){if(!roots.add(c.getType()))throw new IllegalStateException("Duplicate patch class");types.put(c.getType(),c);replacements.add(c);merged.add(c);}
  for(String root:ROOTS)if(!roots.contains(OWNER+root+";"))throw new IllegalStateException("Missing replacement root "+root);
  for(ClassDef c:replacements)for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)i).getReference();if(ref instanceof MethodReference){MethodReference method=(MethodReference)ref;String owner=method.getDefiningClass();if(owner.startsWith(OWNER)||owner.startsWith("Lai/onnxruntime/")){if(!resolves(method,owner,new HashSet<>()))throw new IllegalStateException("Missing runtime method "+method);contracts++;}}}
  DexPool.writeTo(args[2],new ImmutableDexFile(base.getOpcodes(),merged));
  System.out.println("Preserved "+(base.getClasses().size()-removed)+" classes; replaced "+removed+" with "+replacements.size()+"; verified "+contracts+" method contracts");
 }
}
