package org.jetbrains.emacs4ij.jelisp.subroutine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.emacs4ij.jelisp.BufferEnvironment;
import org.jetbrains.emacs4ij.jelisp.Environment;
import org.jetbrains.emacs4ij.jelisp.GlobalEnvironment;
import org.jetbrains.emacs4ij.jelisp.JelispBundle;
import org.jetbrains.emacs4ij.jelisp.elisp.*;
import org.jetbrains.emacs4ij.jelisp.exception.*;
import org.jetbrains.emacs4ij.jelisp.interactive.EmptyReader;
import org.jetbrains.emacs4ij.jelisp.interactive.SpecialFormInteractive;
import org.jetbrains.emacs4ij.jelisp.platform_dependent.LispBuffer;
import org.jetbrains.emacs4ij.jelisp.platform_dependent.LispMinibuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.emacs4ij.jelisp.subroutine.Predicate.isNil;
import static org.jetbrains.emacs4ij.jelisp.subroutine.Predicate.subrp;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina.Polishchuk
 * Date: 7/13/11
 * Time: 3:49 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Core {
    private Core() {}

    public static LispObject thisOrNil (@Nullable LispObject object) {
        return object == null ? LispSymbol.ourNil : object;
    }

    public static void error (String message) {
        ArrayList<LispObject> data = new ArrayList<>();
        data.add(new LispSymbol("error"));
        data.add(new LispString(message));
        LispList.list(data).evaluate(GlobalEnvironment.INSTANCE);
    }

    @Subroutine("set")
    public static LispObject set (Environment environment, LispSymbol variable, LispObject initValue) {
        LispObject value = (initValue == null) ? LispSymbol.ourVoid : initValue;
        LispSymbol symbol = new LispSymbol(variable.getName(), value);
        environment.setVariable(symbol);
        return value;
    }

    public static boolean equals (LispObject one, LispObject two) {
        return one.equals(two);
    }

    @Subroutine("equal")
    public static LispObject equal (LispObject one, LispObject two) {
        return LispSymbol.bool(equals(one, two));
    }

    public static boolean eqs (LispObject one, LispObject two) {
        if (one == two) return true;
        if (one.getClass() != two.getClass()) return false;
        if (one instanceof LispNumber) {
            return (((LispNumber) one).getData()  == ((LispNumber) two).getData());
        }
        if (one instanceof LispSymbol) {
            return ((LispSymbol) one).getName().equals(((LispSymbol) two).getName());
        }
        return one instanceof LispString
                && ((LispString) one).getData().equals("")
                && ((LispString) two).getData().equals("");
        //all other types eq by reference
    }

    @Subroutine("eq")
    public static LispObject eq (LispObject one, LispObject two) {
        return LispSymbol.bool(eqs(one, two));
    }

    @Subroutine("null")
    public static LispObject lispNull (LispObject lObject) {
        return LispSymbol.bool(lObject.equals(LispSymbol.ourNil));
    }

    @Subroutine("not")
    public static LispObject lispNot (LispObject lObject) {
        return lispNull(lObject);
    }

    private static LispObject getArgumentsForCall (Environment environment, LispList interactiveForm) {
        LispObject body = interactiveForm.cdr();
        if (body.equals(LispSymbol.ourNil))
            return LispList.list();
        if (!(body instanceof LispList))
            throw new WrongTypeArgumentException("listp", body);
        body = ((LispList) body).car();
        if (body instanceof LispString) {
            return ((LispString) body).getData().equals("") // my primitives have "" interactive string
                    ? LispList.list()
                    : body;
        } else if (body instanceof LispList) {
            LispObject args = body.evaluate(environment);
            if (!(args instanceof LispList))
                throw new WrongTypeArgumentException("listp", args);
            return args;
        } else
            throw new WrongTypeArgumentException("sequencep", body);
    }

    public static LispCommand toCommand (LispObject function) {
        LispObject f = function;
        if (function instanceof LispSymbol)
            f = ((LispSymbol) function).getFunction();
        if (f instanceof LispList) {
            try {
                f = new Lambda((LispList) f);
            } catch (Exception e) {
                return null;
            }
        }
        if ((f instanceof Lambda || f instanceof Primitive) && ((LispCommand)f).isInteractive()) {
            return (LispCommand) f;
        }
        return null;
    }

    @NotNull
    private static LambdaOrSymbolWithFunction normalizeCommand (LispObject command) {
        if (command instanceof LispSymbol) {
            ((LispSymbol) command).castToLambda();
            LispObject function = ((LispSymbol) command).getFunction();
            if ((function instanceof Lambda || function instanceof Primitive) && ((LispCommand)function).isInteractive()) {
                return (LispSymbol) command;
            }
        } else if (command instanceof LispList) {
            try {
                Lambda f = new Lambda((LispList) command);
                if (f.isInteractive())
                    return f;
            } catch (Exception e) {
                throw new InternalException(JelispBundle.message("invalid.command"));
            }
        }
        throw new InternalException(JelispBundle.message("invalid.command"));
    }

    private static void shiftPrefixArgs(Environment environment) {
        environment.setVariable(new LispSymbol("last-prefix-arg", environment.find("current-prefix-arg").getValue()));
        environment.setVariable(new LispSymbol("current-prefix-arg", environment.find("prefix-arg").getValue()));
        environment.setVariable(new LispSymbol("prefix-arg", LispSymbol.ourNil));
    }

    private static void clearPrefixArgs(Environment environment) {
        environment.setVariable(new LispSymbol("last-prefix-arg", environment.find("current-prefix-arg").getValue()));
        environment.setVariable(new LispSymbol("current-prefix-arg", LispSymbol.ourNil));
    }

    public static void shiftCommandVars(LambdaOrSymbolWithFunction command) {
        LispSymbol thisCommand = GlobalEnvironment.INSTANCE.find("this-command");
        LispSymbol lastCommand = GlobalEnvironment.INSTANCE.find("last-command");
        lastCommand.setValue(thisCommand.getValue());
        thisCommand.setValue(command);
    }

    public static LambdaOrSymbolWithFunction getInvoker() {
        LambdaOrSymbolWithFunction invoker = (LambdaOrSymbolWithFunction) GlobalEnvironment.INSTANCE.find("this-command").getValue();
        return invoker.equals(LispSymbol.ourNil) ? null : invoker;
    }

    @Subroutine("call-interactively")
    public static void callInteractively (Environment environment, LispObject function, @Nullable @Optional LispObject recordFlag, @Nullable LispObject keys) {
        LispCommand command = toCommand(function);
        if (command == null)
            throw new WrongTypeArgumentException("commandp", function);

        if (function.equals(new LispSymbol("execute-extended-command")))
            shiftPrefixArgs(environment);
        else clearPrefixArgs(environment);

        shiftCommandVars(normalizeCommand(function));

        LispObject args = getArgumentsForCall(environment, command.getInteractiveForm());
        LispList result = SpecialForms.interactive(environment, args);
        if (result == null)
            return;
        EmptyReader reader = new EmptyReader(environment, getInvoker(), result);
        environment.getMinibuffer().onInteractiveNoIoInput(reader);
    }

    @Subroutine("funcall")
    public static LispObject functionCall (Environment environment, LispObject function, @Optional LispObject... args) {
        List<LispObject> data = new ArrayList<>();
        data.add(function);
        Collections.addAll(data, args);
        LispList funcall = LispList.list(data);
        environment.setArgumentsEvaluated(true);
        environment.setSpecFormsAndMacroAllowed(false);
        return funcall.evaluate(environment);
    }

    @Subroutine("signal")
    public static LispObject signal (LispSymbol errorSymbol, LispList data) {
//        LispObject errorMessage = errorSymbol.getProperty("error-message");
        String msg = "";// '[' + ((errorMessage instanceof LispString) ? ((LispString) errorMessage).getData() : "peculiar error") + "] ";
        msg += '(' + errorSymbol.getName() + ' ';
        msg += (data.size() == 1 ? data.car().toString() : data.toString()) + ')';
//        System.out.println(msg);
        throw new LispException(msg);
    }

    private static void runFunction (Environment environment, LispSymbol function) {
        if (function.equals(LispSymbol.ourNil))
            return;
        function.evaluateFunction(environment, InvalidFunctionException.class, new ArrayList<LispObject>());
    }

    @Subroutine("run-hooks")
    public static LispObject runHooks (Environment environment, @Optional LispSymbol... hooks) {
        if (hooks == null)
            return LispSymbol.ourNil;
        for (LispSymbol hook: hooks) {
            LispSymbol tHook = environment.find(hook.getName());
            if (tHook == null || tHook.equals(LispSymbol.ourNil)
                    || !tHook.hasValue() || tHook.getValue().equals(LispSymbol.ourNil))
                continue;
            if (tHook.getValue() instanceof LispSymbol) {
                runFunction(environment, (LispSymbol) tHook.getValue());
                continue;
            }
            if (tHook.getValue() instanceof LispList) {
                for (LispObject function: ((LispList) tHook.getValue()).toLispObjectList()) {
                    if (!(function instanceof LispSymbol))
                        throw new WrongTypeArgumentException("symbolp", function);

                    LispSymbol tFunction = environment.find(((LispSymbol)function).getName());
                    runFunction(environment, tFunction);
                }
                continue;
            }
            throw new InvalidFunctionException(tHook.getValue().toString());
        }
        return LispSymbol.ourNil;
    }

    @Subroutine("macroexpand")
    public static LispObject macroExpand (Environment environment, LispObject macroCall, @Optional LispObject env) {
        //todo: env
        if (!Predicate.isNil(env))
            System.err.println("macroexpand: emacs environment = " + env.toString());
        if (!(macroCall instanceof LispList))
            return macroCall;
        LispSymbol macro;
        try {
            macro = (LispSymbol) ((LispList) macroCall).car();
        } catch (ClassCastException e) {
            return macroCall;
        }
        LispSymbol trueMacro = environment.find(macro.getName());
        if (trueMacro == null)
            throw new InternalException("void macro " + macro.toString());
        if (!trueMacro.isMacro())
            return macroCall;
        return trueMacro.macroExpand(environment, ((LispList) ((LispList) macroCall).cdr()).toLispObjectList());
    }

    @Subroutine("fset")
    public static LispObject functionSet (Environment environment, LispSymbol symbol, LispObject function) {
        symbol.setFunction(function);
        environment.setVariable(symbol);
        return function;
    }

    @Subroutine("indirect-function")
    public static LispObject indirectFunction (LispObject object, @Optional LispObject noError) {
        if (!(object instanceof LispSymbol)) {
            return object;
        }
        LispSymbol symbol = (LispSymbol) object;
        ArrayList<String> examined = new ArrayList<String>();
        examined.add(symbol.getName());

        while (true) {
            if (!symbol.isFunction()) {
                if (noError != null && !noError.equals(LispSymbol.ourNil))
                    return LispSymbol.ourNil;
                throw new VoidFunctionException(((LispSymbol) object).getName());
                //return signalOrNot(noError, "void-function", symbol.getName());
            }
            LispObject f = symbol.getFunction();
            if (f instanceof LispSymbol) {
                if (examined.contains(((LispSymbol) f).getName())) {
                    if (noError != null && !noError.equals(LispSymbol.ourNil))
                        return LispSymbol.ourNil;
                    throw new CyclicFunctionIndirectionException(symbol.getName());

                    //return signalOrNot(noError, "cyclic-function-indirection", symbol.getName());
                }
                symbol = (LispSymbol) f;
                examined.add(symbol.getName());
                continue;
            }
            return f;
        }
    }

    @Subroutine("subr-arity")
    public static LispObject subrArity (LispObject object) {
        if (subrp(object).equals(LispSymbol.ourNil))
            throw new WrongTypeArgumentException("subrp",
                    object instanceof LispSymbol ? ((LispSymbol) object).getName() : object.toString());
        Primitive subr = (Primitive)object;
        return LispList.cons(new LispInteger(subr.getNRequiredArguments()), subr.getMaxNumArgs());
    }

    @Subroutine("aref")
    public static LispObject aRef (LispArray array, LispInteger index) {
        try {
            return array.getItem(index.getData());
            //todo: bool-vector
//            throw new WrongTypeArgumentException("arrayp", array.toString());
        } catch (IndexOutOfBoundsException e) {
            throw new ArgumentOutOfRange(array.toString(), index.toString());
        }
    }

    @Subroutine("aset")
    public static LispObject aSet (LispArray array, LispInteger index, LispObject value) {
        try {
            array.setItem(index.getData(), value);
            return value;
            //todo: bool-vector
//            throw new WrongTypeArgumentException("arrayp", array.toString());
        } catch (IndexOutOfBoundsException e) {
            throw new ArgumentOutOfRange(array.toString(), index.toString());
        }
    }

    @Subroutine("apply")
    public static LispObject apply (Environment environment, LispObject function, LispObject... args) {
        if (!(args[args.length-1] instanceof LispList) && !args[args.length-1].equals(LispSymbol.ourNil))
            throw new WrongTypeArgumentException("listp", args[args.length-1]);

        ArrayList<LispObject> list = new ArrayList<>();
        list.addAll(Arrays.asList(args).subList(0, args.length - 1));

        if (!args[args.length-1].equals(LispSymbol.ourNil)) {
            List<LispObject> last = ((LispList)args[args.length-1]).toLispObjectList();
            list.addAll(last);
        }
        environment.setArgumentsEvaluated(true);
        environment.setSpecFormsAndMacroAllowed(false);

        LambdaOrSymbolWithFunction lambdaOrSymbol = Sequence.verifyFunction(environment, function);
        LispSymbol f;
        if (lambdaOrSymbol instanceof Lambda) {
            f = new LispSymbol("*tmp*");
            f.setFunction(lambdaOrSymbol);
        } else {
            f = (LispSymbol) lambdaOrSymbol;
        }
        return f.evaluateFunction(environment, InvalidFunctionException.class, list);
    }

    @Subroutine(value = "purecopy")
    public static LispObject pureCopy (LispObject object) {
        return object;
    }

    @Subroutine(value = "eval")
    public static LispObject evaluate (Environment environment, LispObject object) {
        return object.evaluate(environment);
    }

    @Subroutine("defalias")
    public static LispObject defineFunctionAlias(LispSymbol symbol, LispObject functionDefinition, @Optional LispObject docString) {
        LispSymbol real = GlobalEnvironment.INSTANCE.find(symbol.getName());
        if (real == null)
            real = new LispSymbol(symbol.getName());
        real.setFunction(functionDefinition);
        if (docString != null && !(docString instanceof LispNumber)) {
            real.setFunctionDocumentation(docString);
        }
        GlobalEnvironment.INSTANCE.defineSymbol(real);
        return functionDefinition;
    }

    @Subroutine("provide")
    public static LispSymbol provide (LispSymbol feature, @Optional LispObject subFeatures) {
        //todo: autoloadable features

        LispSymbol featuresSymbol = GlobalEnvironment.INSTANCE.find("features");
        LispObject featuresValue = featuresSymbol.getValue();
        if (!(featuresValue instanceof LispList))
            throw new WrongTypeArgumentException("listp", featuresValue);

        LispSymbol realFeature;
        try {
            realFeature = feature.uploadVariableDefinition();
        } catch (CyclicDefinitionLoadException | VoidVariableException e) {
            GlobalEnvironment.INSTANCE.defineSymbol(feature);
            realFeature = feature;
        }

        if (!Predicate.isNil(subFeatures)) {
            if (subFeatures instanceof LispList)
                Symbol.put(GlobalEnvironment.INSTANCE, realFeature, new LispSymbol("subfeatures"), subFeatures);
            else throw new WrongTypeArgumentException("listp", subFeatures);
        }

        LispList features = (LispList) featuresValue;
        if (features.memq(realFeature, "eq").isEmpty())
            featuresSymbol.setValue(LispList.cons(realFeature, features));

        return realFeature;
    }

    @Subroutine("atom")
    public static LispSymbol atom (LispObject object) {
        return LispSymbol.bool(!(object instanceof LispList));
    }

    @Subroutine("throw")
    public static void lispThrow (LispObject tag, LispObject value) {
        throw new LispThrow(tag, value);
    }

    @Subroutine("identity")
    public static LispObject identity (LispObject arg) {
        return arg;
    }

    @Subroutine(value = "execute-extended-command", isCmd = true, interactive = "P", key = "\\M-x")
    public static void executeExtendedCommand (Environment environment, LispObject prefixArg) {
        environment.setVariable(new LispSymbol("prefix-arg", prefixArg));
        SpecialFormInteractive interactive = new SpecialFormInteractive(environment,
                new LispSymbol("call-interactively"), "CM-x ");
        LispMinibuffer miniBuffer = environment.getMinibuffer();
        miniBuffer.onInteractiveNoIoInput(interactive);
    }

    private static LispString print (Environment environment, LispObject object,
                                     @Optional LispObject printCharFun, boolean quoteStrings) {
        LispString result = quoteStrings
                ? new LispString(object.toString())
                : object instanceof LispString ? (LispString) object : new LispString(object.toString());
        LispObject toInsert = object instanceof LispString ? result : object;
        if (isNil(printCharFun))
            printCharFun = environment.find("standard-output").getValue();
        if (printCharFun instanceof LispBuffer) {
            ((LispBuffer) printCharFun).insert(toInsert);
        } else if (printCharFun instanceof LispMarker) {
            ((LispMarker) printCharFun).insert(toInsert);
        } else if (printCharFun.equals(LispSymbol.ourT)) {
            GlobalEnvironment.echo(object.toString(), GlobalEnvironment.MessageType.OUTPUT);
        } else {
            for (LispObject character: result.toLispObjectList()) {
                functionCall(environment, printCharFun, character);
            }
        }
        return result;
    }

    @Subroutine("prin1")
    public static LispString prin1 (Environment environment, LispObject object, @Optional LispObject printCharFun) {
        return print(environment, object, printCharFun, true);
    }

    @Subroutine("princ")
    public static LispString princ (Environment environment, LispObject object, @Optional LispObject printCharFun) {
        return print(environment, object, printCharFun, false);
    }

    @Subroutine("abort-recursive-edit")
    public static void abortRecursiveEdit (Environment environment) {
        if (environment.getMiniBufferActivationsDepth() > 0)
            environment.killBuffer(environment.getMinibuffer());
    }

    @Subroutine("recursion-depth")
    public static LispInteger recursionDepth (Environment environment) {
        return new LispInteger(environment.getMiniBufferActivationsDepth());
    }

    @Subroutine("defvaralias")
    public static LispObject defineVariableAlias(Environment environment, LispSymbol aliasVar, LispSymbol baseVar,
                                                 @Optional LispObject docString) {
        LispSymbol alias = environment.find(aliasVar.getName());
        if (alias == null)
            alias = new LispSymbol(aliasVar.getName());
        LispSymbol base = environment.find(baseVar.getName());
        if (base == null) {
            base = new LispSymbol(baseVar.getName());
            GlobalEnvironment.INSTANCE.defineSymbol(base);
        }
        alias.setAsAlias(base);
        if (docString != null && !(docString instanceof LispNumber)) {
            alias.setVariableDocumentation(docString);
        }
        GlobalEnvironment.INSTANCE.defineSymbol(alias);
        if (environment instanceof BufferEnvironment && environment.containsSymbol(base.getName()))
            environment.defineSymbol(alias);
        return base;
    }

    @Subroutine("require")
    public static void require (LispObject feature, @Optional LispObject fileName, LispObject noError) {
        //todo: implement
    }

    @Subroutine("load-average")
    public static LispObject loadAverage (@Optional LispObject useFloats) {
        return LispSymbol.ourNil;
    }

    @Subroutine("autoload")
    public static LispObject autoLoad (Environment environment, LispSymbol function, LispString fileName,
                                       @Optional LispObject doc, LispObject interactive, LispObject type) {
        LispSymbol f = environment.find(function.getName());
        if (f != null && f.isFunction())
            return LispSymbol.ourNil;
        LispList list = LispList.list(Arrays.asList(new LispSymbol("autoload"),
                fileName, Core.thisOrNil(doc), Core.thisOrNil(interactive), Core.thisOrNil(type)));
        if (f == null) {
            f = new LispSymbol(function.getName());
            GlobalEnvironment.INSTANCE.defineSymbol(f);
        }
        f.setFunction(list);
        return LispList.list(list);
    }

    @Subroutine(value = "start-kbd-macro", isCmd = true, interactive = "P")
    public static LispSymbol startKbdMacro (Environment environment, LispObject array, @Optional LispObject noExec) {
        //todo: implement
        return LispSymbol.ourNil;
    }

}