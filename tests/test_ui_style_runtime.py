"""A .mtui is taken whole or not at all; a built-in style written out reads back the same."""
import glob
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
# android.jar's json, stood in for by the same library on the computer
JSON_JAR = next(iter(sorted(j for j in glob.glob(os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/org.json/json/*/*/json-*.jar")) if "sources" not in j)), None)


def text_stub():
    source = (ROOT / "inject/java/cat/narezany/margyt/Text.java").read_text(encoding="utf-8")
    names = sorted(set(re.findall(r"static final String (UI_FILE_\w+)", source)))
    return "package cat.narezany.margyt;class Text{" + "".join(
        'static final String %s="%s";' % (name, name) for name in names) + "}"


STUBS = {
    "android/content/Context.java": "package android.content;public class Context{public java.io.File getFilesDir(){"
        "return new java.io.File(System.getProperty(\"java.io.tmpdir\"));}}",
    "cat/narezany/margyt/Diary.java": "package cat.narezany.margyt;class Diary{static void note(String s){}}",
}

HARNESS = r'''package cat.narezany.margyt;
public class Test{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static String refused(String json){try{UiStyle.read(json.getBytes("UTF-8"),null);return null;}catch(UiStyle.Refused r){return r.getMessage();}catch(Exception e){throw new RuntimeException(e);}}
 public static void main(String[] a)throws Exception{
  UiStyle aero=UiStyle.read(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])),null);
  check(aero.name.equals("Aero")&&aero.base==UiStyle.GLASS&&aero.controls==UiStyle.FLUENT,"the example is read");
  check(aero.card==20&&aero.bar==-1&&aero.gap==3&&aero.blur==2.2f&&aero.duration==240,"its numbers");
  check(aero.key.equals("custom:aero"),"kept under its name: "+aero.key);
  // what is left out comes from the base
  UiStyle thin=UiStyle.read("{\"mtui\":1,\"name\":\"Thin\",\"base\":\"fluent\",\"corners\":{\"card\":12}}".getBytes("UTF-8"),null);
  check(thin.card==12&&thin.small==UiStyle.ACRYLIC.small&&thin.duration==180&&thin.controls==UiStyle.FLUENT&&thin.neutral,"the rest is Fluent's");
  check(refused("[]").equals(Text.UI_FILE_NOT_JSON),"not json");
  check(refused("{\"name\":\"x\",\"base\":\"md3\"}").equals(Text.UI_FILE_NOT_UI),"no format number");
  check(refused("{\"mtui\":2,\"name\":\"x\",\"base\":\"md3\"}").equals(Text.UI_FILE_NEWER),"a newer format");
  check(refused("{\"mtui\":1,\"base\":\"md3\"}").equals(Text.UI_FILE_NO_NAME),"no name");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"aero\"}").equals(Text.UI_FILE_NO_BASE),"an unknown base");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"corners\":{\"card\":90}}").startsWith(Text.UI_FILE_BAD_VALUE),"a corner out of range");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"gap\":\"wide\"}").startsWith(Text.UI_FILE_BAD_VALUE),"a word for a number");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"motion\":5}").startsWith(Text.UI_FILE_BAD_VALUE),"a number for a section");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"controls\":\"ios\"}").startsWith(Text.UI_FILE_BAD_VALUE),"unknown controls");
  check(refused("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"sparkles\":true}")==null,"unknown fields are passed over");
  // the three built in, written out, read back as they were
  for(UiStyle built:new UiStyle[]{UiStyle.MATERIAL,UiStyle.LIQUID,UiStyle.ACRYLIC}){
   UiStyle back=UiStyle.read(built.json().getBytes("UTF-8"),null);
   check(back.base==built.base&&back.controls==built.controls&&back.card==built.card&&back.inner==built.inner&&back.sheet==built.sheet
     &&back.small==built.small&&back.bar==built.bar&&back.gap==built.gap&&back.blur==built.blur&&back.tint==built.tint
     &&back.refraction==built.refraction&&back.dispersion==built.dispersion&&back.grain==built.grain&&back.windowBlur==built.windowBlur
     &&back.duration==built.duration&&back.bounce==built.bounce&&back.press==built.press&&back.neutral==built.neutral&&back.cardLift==built.cardLift,
     built.name+" round trip");
  }
  check(UiStyle.id("Моя Тема 2!").equals("моя-тема-2"),"an id: "+UiStyle.id("Моя Тема 2!"));
  StringBuilder big=new StringBuilder("{\"mtui\":1,\"name\":\"x\",\"base\":\"md3\",\"description\":\"");for(int i=0;i<40000;i++)big.append('a');big.append("\"}");
  check(refused(big.toString()).equals(Text.UI_FILE_BIG),"over 32 KB");
  System.out.println("styles passed");
 }
}'''


class UiStyleTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java") and JSON_JAR, "JDK and org.json required")
    def test_a_style_is_taken_whole_and_the_built_in_ones_read_back(self):
        with tempfile.TemporaryDirectory() as directory:
            work = pathlib.Path(directory)
            sources = dict(STUBS)
            sources["cat/narezany/margyt/Text.java"] = text_stub()
            sources["cat/narezany/margyt/UiStyle.java"] = (ROOT / "inject/java/cat/narezany/margyt/UiStyle.java").read_text(encoding="utf-8")
            sources["cat/narezany/margyt/Test.java"] = HARNESS
            for name, content in sources.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            built = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", JSON_JAR, "-d", str(work)]
                                   + [str(work / n) for n in sources], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            run = subprocess.run(["java", "-cp", str(work) + os.pathsep + JSON_JAR, "cat.narezany.margyt.Test",
                                  str(ROOT / "examples/aero.mtui")], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()
