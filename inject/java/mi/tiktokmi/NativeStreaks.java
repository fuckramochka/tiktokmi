package mi.tiktokmi;
import java.util.*;import java.lang.reflect.*;
import com.ss.android.ugc.aweme.im.streak.api.IStreakService;
/** Audited entry points in TikTok 47.2.41: singleton, login initializer, friend conversation IDs. */
final class NativeStreaks {
 private static String account;private static long last;
 private static volatile String friendsOf;private static volatile long friendsAsked;
 static synchronized void prepare(){
  if(Account.liveId()==null)try{Object userService=Class.forName("X.14EI").getMethod("LJFF").invoke(null);if(userService instanceof com.ss.android.ugc.aweme.IAccountUserService)Account.getCurUserId((com.ss.android.ugc.aweme.IAccountUserService)userService);}catch(Throwable ignored){}
  String uid=Account.liveId();if(uid==null)return;
  long now=android.os.SystemClock.uptimeMillis();if(uid.equals(account)&&now-last<5000)return;account=uid;last=now;
  try{
   Object nativeService=Class.forName("X.06p9").getMethod("LIZ").invoke(null);
   if(!(nativeService instanceof IStreakService))return;
   IStreakService service=(IStreakService)nativeService;
   Streaks.discover(service,null);
   android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
   main.post(()->{if(!uid.equals(Account.liveId()))return;try{service.getClass().getMethod("LIZLLL").invoke(service);}catch(Throwable e){Diary.note("streak native init: "+e.getClass().getSimpleName());}});
   // TikTok's list of friends is read out of JSON as it is asked for, which took
   // nearly half a second; it was asked for on the main thread every minute.
   // Now off it, and once in ten minutes: friends do not change that often.
   if(now-friendsAsked>=600000L||!uid.equals(friendsOf)){friendsAsked=now;friendsOf=uid;
    Net.away("streak friends",()->{try{
     Object api=Class.forName("X.06bA").getMethod("LIZ").invoke(null);
     if(api!=null){Object contacts=api.getClass().getMethod("LJ").invoke(api);if(contacts!=null){Object list=contacts.getClass().getMethod("getAllFriends").invoke(contacts);if(list instanceof List)friends((List)list,uid);}}
    }catch(Throwable e){Diary.note("streak friend list: "+e.getClass().getSimpleName());}});}
  }catch(Throwable e){Diary.note("streak native discovery: "+e.getClass().getSimpleName());}
 }
 /** An account id: up to 24 digits. Checked by hand: a pattern was compiled again for every friend. */
 private static boolean digits(String s){if(s==null||s.isEmpty()||s.length()>24)return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<'0'||c>'9')return false;}return true;}
 static void friends(List<?> users,String uid){
  if(!uid.equals(Account.liveId()))return;
  try{
   Object value=Class.forName("X.06p9").getMethod("LIZ").invoke(null);if(!(value instanceof IStreakService))return;
   IStreakService service=(IStreakService)value;Method convert=Class.forName("X.06WN").getMethod("LJ",String.class);List<String> found=new ArrayList<>();
   for(Object user:users){if(!uid.equals(Account.liveId()))return;String friend=NativeRead.uid(user);if(!digits(friend)||friend.equals(uid))continue;Object id=convert.invoke(null,friend);if(id instanceof String&&!((String)id).isEmpty())found.add((String)id);}
   if(!found.isEmpty())new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!uid.equals(Account.liveId()))return;for(String id:found)Streaks.discover(service,id);});
  }catch(Throwable e){Diary.note("streak friend discovery: "+e.getClass().getSimpleName());}
 }
}
