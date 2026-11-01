package cat.narezany.margyt;
// Ported from TT Extras 1.0.4 (dev.glasstt.extras) with its author's permission.

import java.lang.reflect.*;
final class GhostReflect {
 static Class<?> type(String name)throws Exception{return Class.forName(name,false,GhostReflect.class.getClassLoader());}
 static Field field(Class<?> c,String name)throws Exception{for(Class<?> p=c;p!=null;p=p.getSuperclass())try{Field f=p.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
 static Object stat(String cls,String field)throws Exception{return field(type(cls),field).get(null);}
 static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}

 static Object invoke(Method m,Object o,Object[] args)throws Throwable{try{return m.invoke(o,args);}catch(InvocationTargetException e){throw e.getCause();}}
 static final class Patch {
  final Object owner,previous,replacement;final Field field;
  Patch(Object owner,Field f,Object value)throws Exception{this(owner,f,value,f.get(owner));}
  Patch(Object owner,Field f,Object value,Object restoreValue)throws Exception{this.owner=owner;field=f;previous=restoreValue;replacement=value;f.set(owner,value);if(f.get(owner)!=value)throw new IllegalStateException("write rejected: "+f.getName());}
  void restore()throws Exception{if(field.get(owner)==replacement)field.set(owner,previous);}
 }
}
