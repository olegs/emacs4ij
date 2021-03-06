package org.jetbrains.emacs4ij.jelisp;

import org.jetbrains.emacs4ij.jelisp.elisp.LispList;
import org.jetbrains.emacs4ij.jelisp.elisp.LispObject;
import org.jetbrains.emacs4ij.jelisp.elisp.LispSymbol;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: kate
 * Date: 4/5/12
 * Time: 2:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class DefinitionLoaderTest {
    @BeforeClass
    public static void runBeforeClass() {
        TestSetup.runBeforeClass();
    }

    @Test
    public void testInit() {
        DefinitionLoader.test();
        System.out.println(DefinitionLoader.myIndex.size());
    }

    @Test
    public void testGetFunctionFromFile() {
        String lispObjectFileNameFile = GlobalEnvironment.getEmacsSource() + "/lisp/help-fns.el";
        String lispFunctionName = "find-lisp-object-file-name";
        LispList functionFromFile = DefinitionLoader.getDefFromFile(lispObjectFileNameFile, lispFunctionName,
                DefinitionLoader.DefType.FUN);
        Assert.assertEquals(new LispSymbol(lispFunctionName), ((LispList) functionFromFile.cdr()).car());
    }

    @Test
    public void testGetDef() {
        LispList def = DefinitionLoader.getDefFromFile("/home/kate/Downloads/emacs-23.4/lisp/edmacro.el",
                "edmacro-parse-keys", DefinitionLoader.DefType.FUN);
        Assert.assertNotNull(def);
    }

    @Test
    public void testGetDef1() {
        LispList def = DefinitionLoader.getDefFromFile("/home/kate/Downloads/emacs-23.4/lisp/simple.el",
                "region-active-p", DefinitionLoader.DefType.FUN);
        Assert.assertNotNull(def);
    }

    @Test
    public void testFindMark () throws Throwable {
        try {
            DefinitionLoader.getDefFromFile(GlobalEnvironment.getEmacsSource() + "/lisp/simple.el", "mark",
                    DefinitionLoader.DefType.FUN);
        } catch (Exception e) {
            System.out.println(TestSetup.getCause(e));
        }
    }

    @Test
    public void testParseDefineMinorMode() {
        GlobalEnvironment.setEmacsSource("/home/kate/Downloads/emacs-23.4");
        LispObject dmm = DefinitionLoader.getDefFromFile(GlobalEnvironment.getEmacsSource() + "/lisp/emacs-lisp/easy-mmode.el",
                "define-minor-mode", DefinitionLoader.DefType.FUN);
        Assert.assertNotNull(dmm);
    }

    @Test
    public void testDefFormsAreInRightPlaces() {
        Map<String, Long> filename = DefinitionLoader.getFileName("defcustom", DefinitionLoader.DefType.FUN);
        Assert.assertNotNull(filename);
        Assert.assertTrue(containsStringWhichEndsWith(filename, "/lisp/custom.el"));
        Assert.assertTrue(containsStringWhichEndsWith(
                DefinitionLoader.getFileName("defsubst", DefinitionLoader.DefType.FUN), "/lisp/emacs-lisp/byte-run.el"));
        Assert.assertTrue(containsStringWhichEndsWith(
                DefinitionLoader.getFileName("defgroup", DefinitionLoader.DefType.FUN), "/lisp/custom.el"));
        Assert.assertTrue(containsStringWhichEndsWith(
                DefinitionLoader.getFileName("defface", DefinitionLoader.DefType.FUN), "/lisp/custom.el"));
    }

    private boolean containsStringWhichEndsWith (Map<String, Long> map, String ending) {
        for (String s: map.keySet())
            if (s.endsWith(ending))
                return true;
        return false;
    }

    @Test
    public void testContainsDef() {
        String line = "(defvar emacs-lisp-mode-syntax-table";
        Identifier id = new Identifier("emacs-lisp-mode-syntax-table", DefinitionLoader.DefType.VAR);
        int condition = DefinitionLoader.defStartIndex(line, id.getName(), (id.getType() == DefinitionLoader.DefType.FUN
                ? DefinitionLoader.myDefFuns : DefinitionLoader.myDefVars));
        Assert.assertTrue(condition != -1);
    }

    //todo: restore
//    @Test
//    public void testDefineAll() {
//        GlobalEnvironment.TEST = true;
//        for (Map.Entry<Identifier, SortedMap<String,Long>> entry:  DefinitionLoader.myIndex.entrySet()) {
//            for (Map.Entry<String, Long> location: entry.getValue().entrySet()) {
//                try {
//                    LispList def = DefinitionLoader.FileScanner
//                            .getDefFromFile(location.getKey(), location.getValue(), entry.getKey());
//                    DefinitionLoader.FileScanner.onUploadFinish(entry.getKey());
////                    if (def == null)
////                        System.out.print(1);
////                    Assert.assertNotNull(def);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    @Test
    public void testContainDef() {
        String line = "(defvar org-id-track-globally)";
        String name = "org-id-track-globally";
        Assert.assertTrue(DefinitionLoader.defStartIndex(line, name, DefinitionLoader.myDefVars) == 0);
    }

    @Test
    public void testLoad() {
        DefinitionLoader.loadFile("jit-lock.el");
        Assert.assertNotNull(GlobalEnvironment.INSTANCE.find("with-buffer-prepared-for-jit-lock"));
    }
}
