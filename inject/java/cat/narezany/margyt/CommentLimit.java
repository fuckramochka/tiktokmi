package cat.narezany.margyt;
// Ported from TT Extras 1.0.4 (dev.glasstt.extras) with its author's permission.

/** Copy the real comment configuration; preserve its display/expansion settings. */
final class CommentLimit {
 static Object withLimit(Object original,int limit)throws Exception{
  if(limit<1)throw new IllegalArgumentException("limit");
  Class<?>
 type=original.getClass();
  return type.getConstructor(boolean.class,int.class,int.class,int.class).newInstance(
   GhostReflect.field(type,"enable").getBoolean(original),
   GhostReflect.field(type,"foldLines").getInt(original),
   GhostReflect.field(type,"expandLinesStep").getInt(original),limit);
 }
}
