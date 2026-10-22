"""The old profile header moved into the new shape, and nothing else touched."""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from margyt import dexpatch  # noqa: E402

STUBS = {
    'com/ss/android/ugc/aweme/profile/model/User.java': 'package com.ss.android.ugc.aweme.profile.model;public class User{}',
    'com/ss/android/ugc/profile/platform/base/data/ProfileComponents.java':
        'package com.ss.android.ugc.profile.platform.base.data;public class ProfileComponents{public String componentId,componentName;'
        'public java.util.List<? extends ProfileComponents> components;}',
    'com/ss/android/ugc/profile/platform/base/data/ProfileUser.java':
        'package com.ss.android.ugc.profile.platform.base.data;public class ProfileUser{public ProfileComponents navBarComponents,headerComponents,tabsComponents,profileBackground;}',
    'kotlin/jvm/functions/Function1.java': 'package kotlin.jvm.functions;public interface Function1<P,R>{R invoke(P p);}',
    'cat/narezany/margyt/Flags.java': 'package cat.narezany.margyt;class Flags{static boolean on=true;static int forgot;static boolean newProfile(){return on;}static void forget(){forgot++;}}',
    'cat/narezany/margyt/Diary.java': 'package cat.narezany.margyt;class Diary{static void note(String s){}}',
    'android/content/SharedPreferences.java': 'package android.content;public interface SharedPreferences{int getInt(String k,int d);boolean getBoolean(String k,boolean d);'
        'Editor edit();interface Editor{Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);void apply();}}',
    'android/content/Context.java': 'package android.content;public class Context{public static final int MODE_PRIVATE=0;'
        'public SharedPreferences getSharedPreferences(String n,int m){return Prefs.ONE;}}',
    'android/content/Prefs.java': 'package android.content;public class Prefs implements SharedPreferences{public static final Prefs ONE=new Prefs();'
        'public java.util.Map<String,Object> kept=new java.util.HashMap<>();public int getInt(String k,int d){Object v=kept.get(k);return v==null?d:(Integer)v;}'
        'public boolean getBoolean(String k,boolean d){Object v=kept.get(k);return v==null?d:(Boolean)v;}public Editor edit(){final Prefs p=this;return new Editor(){'
        'public Editor putBoolean(String k,boolean v){p.kept.put(k,v);return this;}public Editor putInt(String k,int v){p.kept.put(k,v);return this;}public void apply(){}};}}',
    'cat/narezany/margyt/Margy.java': 'package cat.narezany.margyt;class Margy{static final String PREFS="margyt";static android.content.Context context(){return new android.content.Context();}}',
    # TikTok's own method, which the mod calls after moving the parts
    'X/0sm9.java': None,
}

HARNESS = r'''package cat.narezany.margyt;
import com.ss.android.ugc.profile.platform.base.data.*;import java.util.*;
public class HeaderHarness{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static ProfileComponents c(String name,ProfileComponents... kids){ProfileComponents p=new ProfileComponents();p.componentId=name;p.componentName=name;p.components=kids.length==0?null:new ArrayList<ProfileComponents>(Arrays.asList(kids));return p;}
 static String shape(ProfileComponents p){StringBuilder s=new StringBuilder(p.componentName.length()==0?"(empty)":p.componentName);if(p.components!=null){s.append('[');for(int i=0;i<p.components.size();i++){if(i>0)s.append(',');s.append(shape(p.components.get(i)));}s.append(']');}return s.toString();}
 static ProfileComponents names(){return c("user_info",c("user_account_name_info",c("user_info_nickname")),c("user_account_base_info",c("user_account_user_name")));}
 // the new header as the server sends it to an account in the new group, read off a phone
 static ProfileUser realMine(){ProfileUser p=new ProfileUser();ProfileComponents n=names();n.components=new ArrayList<ProfileComponents>(n.components);((List<ProfileComponents>)n.components).add(c("user_relation_info",c("relation_info_following"),c("relation_info_follower")));
  p.headerComponents=c("header",c("info",c("user_basic_info",n,c("header_avatar",c("avatar_normal")))),c("bio",c("bio_signature"),c("bio_profile_link")),c("advanced_feature",c("advanced_feature_balance")),c("story_highlight"));return p;}
 static ProfileUser realOther(){ProfileUser p=new ProfileUser();ProfileComponents n=names();n.components=new ArrayList<ProfileComponents>(n.components);((List<ProfileComponents>)n.components).add(c("user_relation_info",c("relation_info_following")));
  p.headerComponents=c("header",c("info",c("user_basic_info",c(""),n,c("header_avatar",c("avatar_normal")))),c("cta",c("cta_social_follow")),c("bio",c("bio_signature")));return p;}
 public static void main(String[] a){
  // the old header, as TikTok's own template for the old group builds it
  ProfileUser old=new ProfileUser();
  old.headerComponents=c("header",c("header_avatar",c("avatar_normal")),c("info",names(),c("user_relation_info",c("relation_info_following"),c("relation_info_follower"))),c("bio",c("bio_signature"),c("bio_profile_link")),c("advanced_feature",c("advanced_feature_balance")),c("story_highlight"));
  check(ProfileHeader.rearrange(old,true),"the old header is moved");
  check(shape(old.headerComponents).equals(shape(realMine().headerComponents)),"into exactly what the new group gets: "+shape(old.headerComponents));
  check(!ProfileHeader.rearrange(old,true),"and a second time does nothing");
  ProfileUser theirs=new ProfileUser();
  theirs.headerComponents=c("header",c("header_avatar",c("avatar_normal")),c("info",names(),c("user_relation_info",c("relation_info_following"))),c("cta",c("cta_social_follow")),c("bio",c("bio_signature")));
  check(ProfileHeader.rearrange(theirs,false)&&shape(theirs.headerComponents).equals(shape(realOther().headerComponents)),"someone else's, with the spacer: "+shape(theirs.headerComponents));
  ProfileUser fresh=realMine();String before=shape(fresh.headerComponents);
  check(!ProfileHeader.rearrange(fresh,true)&&shape(fresh.headerComponents).equals(before),"a new header is left alone");
  ProfileUser odd=new ProfileUser();odd.headerComponents=c("header",c("something_else"));check(!ProfileHeader.rearrange(odd,true),"and one it does not know");
  ProfileUser empty=new ProfileUser();check(!ProfileHeader.rearrange(empty,true),"and none at all");
  // through TikTok's method: moved, remembered as the old group, handed on
  ProfileUser page=new ProfileUser();page.headerComponents=c("header",c("header_avatar"),c("info",names(),c("user_relation_info")),c("cta"),c("bio"));
  check(ProfileHeader.LJIILIIL(null,page,false,null)==page&&shape(page.headerComponents).startsWith("header[info[user_basic_info[(empty),user_info"),"the call moves then hands on");
  check(X.Calls.count==1,"TikTok's own method ran once");
  check(android.content.Prefs.ONE.getBoolean("profile_old_group",false)&&Flags.forgot==1,"an old header seen is remembered as the old group");
  // a new page through the call is the page the server sent
  ProfileUser real=realMine();String sent=shape(real.headerComponents);
  ProfileHeader.LJIILIIL(null,real,true,null);check(shape(real.headerComponents).equals(sent),"a new page passes as it came: "+shape(real.headerComponents));
  Flags.on=false;ProfileUser kept=new ProfileUser();kept.headerComponents=c("header",c("header_avatar"),c("info",c("user_info")));
  ProfileHeader.LJIILIIL(null,kept,false,null);check(shape(kept.headerComponents).startsWith("header[header_avatar"),"with the switch off the page is TikTok's");
  System.out.println("header passed");
 }
}'''

TIKTOK = '''package X;
public class Calls{public static int count;}
'''


class ProfileHeaderTest(unittest.TestCase):
    def test_the_build_sends_every_page_through_the_mod(self):
        rules = [rule for rule in dexpatch.model_rules() if rule[0] == "0sm9->LJIILIIL"]
        self.assertEqual(len(rules), 1)
        _label, pattern, target = rules[0]
        call = ("    invoke-static {v0, v1, v2, v3}, LX/0sm9;->LJIILIIL(Lcom/ss/android/ugc/aweme/profile/model/User;"
                "Lcom/ss/android/ugc/profile/platform/base/data/ProfileUser;ZLkotlin/jvm/functions/Function1;)"
                "Lcom/ss/android/ugc/profile/platform/base/data/ProfileUser;\n")
        out, hits = pattern.subn(target, call)
        self.assertEqual(hits, 1)
        self.assertIn("Lcat/narezany/margyt/ProfileHeader;->LJIILIIL(", out)
        with open(ROOT / "margyt/build.py", encoding="utf-8") as handle:
            self.assertIn('feature_hits["native profile header"] += counts.get("0sm9->LJIILIIL",0)', handle.read())

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_the_old_header_is_moved_into_the_new_shape(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = {k: v for k, v in STUBS.items() if v is not None}
            # "0sm9" is not a name javac allows, so TikTok's class is compiled
            # under another and renamed in the class file's own bytes
            sources['X/Zsm9.java'] = ('package X;public class Zsm9{public static com.ss.android.ugc.profile.platform.base.data.ProfileUser '
                                      'LJIILIIL(com.ss.android.ugc.aweme.profile.model.User u,com.ss.android.ugc.profile.platform.base.data.ProfileUser p,'
                                      'boolean m,kotlin.jvm.functions.Function1 f){Calls.count++;return p;}}')
            sources['X/Calls.java'] = TIKTOK
            sources['cat/narezany/margyt/ProfileHeader.java'] = (ROOT / 'inject/java/cat/narezany/margyt/ProfileHeader.java').read_text(encoding='utf-8')
            sources['cat/narezany/margyt/HeaderHarness.java'] = HARNESS
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8')
            built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work)] + [str(work / n) for n in sources],
                                   capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            renamed = (work / 'X/Zsm9.class').read_bytes().replace(b'X/Zsm9', b'X/0sm9')
            (work / 'X/0sm9.class').write_bytes(renamed)
            run = subprocess.run(['java', '-cp', str(work), 'cat.narezany.margyt.HeaderHarness'], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)


if __name__ == '__main__':
    unittest.main()
