package org.jetbrains.emacs4ij.jelisp.elisp;

import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.emacs4ij.jelisp.CustomEnvironment;
import org.jetbrains.emacs4ij.jelisp.Environment;
import org.jetbrains.emacs4ij.jelisp.JelispBundle;
import org.jetbrains.emacs4ij.jelisp.exception.*;
import org.jetbrains.emacs4ij.jelisp.subroutine.Core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: kate
 * Date: 10/6/11
 * Time: 5:46 PM
 * To change this template use File | Settings | File Templates.
 */
public final class Lambda implements FunctionCell, LambdaOrSymbolWithFunction {
    private List<LambdaArgument> myArgumentList = new LinkedList<>();
    private LispObject myDocumentation = null;
    private LispList myInteractive = null;
    private List<LispObject> myBody = new ArrayList<>();
    private int nRequiredArguments = 0;
    private boolean infiniteArgs = false;
    private int nKeywords = 0;

    public Lambda (LispList def) {
        List<LispObject> data = def.toLispObjectList();
        if (!data.get(0).equals(new LispSymbol("lambda")))
            throw new InvalidFunctionException(def.toString());
        try {
            if (!data.get(1).equals(LispSymbol.ourNil))
                parseArgumentsList((LispList) data.get(1));
        } catch (ClassCastException e) {
            throw new InvalidFunctionException(def.toString());
        }
        if (data.size() > 2) {
            try {
                //todo: if those instructions have some side effects, this is wrong behaviour
                //LispObject docString = data.get(2).evaluate(environment);
                LispObject docString = data.get(2);
                if (docString instanceof LispString) {
                    myDocumentation = docString;
                }
            } catch (LispException e) {
                myDocumentation = null;
            }
            myBody = data.subList(2, data.size());
            for (LispObject bodyForm : myBody) {
                if (bodyForm instanceof LispList && !((LispList) bodyForm).isEmpty()) {
                    if (((LispList) bodyForm).car().equals(new LispSymbol("interactive")) && myInteractive == null) {
                        myInteractive = (LispList) bodyForm;
//                        myBody.remove(bodyForm);
                        break;
                    }
                }
            }
        }
    }

    //todo: for test only
    public List<LambdaArgument> getArguments () {
        return myArgumentList;
    }

    //todo: for test only
    public int getBodyLength() {
        return myBody.size();
    }
    
    public void parseArgumentsList (LispList args) {
        nRequiredArguments = 0;
        myArgumentList = new LinkedList<>();
        if (args.isEmpty())
            return;
        List<LispObject> data = args.toLispObjectList();
        LambdaArgument.Type type = LambdaArgument.Type.REQUIRED;
        for (LispObject aData : data) {
            if (aData instanceof LispSymbol) {
                if (aData.equals(new LispSymbol("&rest"))) {
                    type = LambdaArgument.Type.REST;
                    infiniteArgs = true;
                    continue;
                }
                if (aData.equals(new LispSymbol("&optional"))) {
                    type = LambdaArgument.Type.OPTIONAL;
                    continue;
                }
                if (aData.equals(new LispSymbol("&key"))) {
                    type = LambdaArgument.Type.KEYWORD;
                    continue;
                }
            }
            myArgumentList.add(new LambdaArgument(type, aData, "lambda"));
            if (type == LambdaArgument.Type.REQUIRED)
                nRequiredArguments++;
            else if (type == LambdaArgument.Type.KEYWORD)
                nKeywords++;
        }
    }
    
    private String argumentListString () {
        if (myArgumentList.isEmpty())
            return "nil";
        StringBuilder sb = new StringBuilder("(");
        for (LambdaArgument arg: myArgumentList) {
            sb.append(arg.toString()).append(" ");
        }
        return sb.toString().trim() + ')';
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(lambda " + argumentListString());
        for (LispObject bodyForm: myBody) {
            sb.append(" ").append(bodyForm.toString());
        }
        return sb.toString() + ')';
    }

    @Override
    public LispObject evaluate(Environment environment) {
        throw new DirectEvaluationException("lambda");
    }

    private LispObject evaluateBody (Environment inner) {
        LispObject result = LispSymbol.ourNil;
        for (LispObject bodyForm: myBody) {
            result = bodyForm.evaluate(inner);
        }
        return result;
    }

    public LispObject evaluate(Environment environment, List<LispObject> args) {
        return evaluateBody(substituteArguments(environment, args));
    }
    
    private boolean checkOversize(int n) {
        return !infiniteArgs && myArgumentList.size() + nKeywords * 2 < n;
    }

    public CustomEnvironment substituteArguments (Environment environment, List<LispObject> args) {
        if (nRequiredArguments > args.size() || checkOversize(args.size()))
            throw new WrongNumberOfArgumentsException(toString(), args.size());

        CustomEnvironment inner = new CustomEnvironment(environment);
        if (!myArgumentList.isEmpty()) {
            int j = args.size();
            for (int i = 0, argsSize = args.size(); i < argsSize; i++) {
                LispObject argValue = args.get(i);
                LambdaArgument argument = myArgumentList.get(i);
                if (argument.getType() == LambdaArgument.Type.REQUIRED || argument.getType() == LambdaArgument.Type.OPTIONAL) {
                    argument.setValue(inner, argValue);
                    continue;
                }
                if (argument.getType() == LambdaArgument.Type.KEYWORD) {                    
                    LispObject keyword = argValue.evaluate(inner); //todo: if this code has side effects, we shouldn't do this!
                    if (!(keyword instanceof LispSymbol))
                        throw new WrongTypeArgumentException("keyword", keyword.getClass().getSimpleName()); 
                    if (!argument.getKeyword().equals(keyword)) {
                        //can the keywords go in random order? Now can't. + this saves us from unknown keywords
                        throw new WrongTypeArgumentException(argument.getKeyword().getName(), ((LispSymbol) keyword).getName());
                    }
                    if (i+1 >= argsSize) {
                        throw new InternalException(JelispBundle.message("keyword.no.value", keyword));
                    }
                    argument.setValue(inner, args.get(i+1));
                    i++;
                    continue;
                }
                argument.setValue(inner, LispList.list(args.subList(i, argsSize)));
                j = i + 1;
                break;
            }
            for (int k = j; k < myArgumentList.size(); ++k)
                myArgumentList.get(k).setValue(inner, null);
        }
//        System.out.println("--------");
        return inner;
    }

    @Override
    public LispObject getDocumentation() {
        return Core.thisOrNil(myDocumentation);
    }

    @Override
    public void setDocumentation(LispObject doc) {
        myDocumentation = doc;
    }

    @Override
    public boolean isInteractive() {
        return myInteractive != null;
    }

    @Override
    public String getInteractiveString () {
        LispObject args = myInteractive.cdr();
        if (args.equals(LispSymbol.ourNil) || !(args instanceof LispList))
            return null;
        LispObject first = ((LispList) args).toLispObjectList().get(0);
        if (first instanceof LispString) {
            return ((LispString) first).getData();
        }
        throw new NotImplementedException("Not string interactive form: " + myInteractive.toString());
    }

    @Override
    public int getNRequiredArguments() {
        return nRequiredArguments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lambda)) return false;

        Lambda lambda = (Lambda) o;

        if (infiniteArgs != lambda.infiniteArgs) return false;
        if (nKeywords != lambda.nKeywords) return false;
        if (nRequiredArguments != lambda.nRequiredArguments) return false;
        if (myArgumentList != null ? !myArgumentList.equals(lambda.myArgumentList) : lambda.myArgumentList != null)
            return false;
        if (myBody != null ? !myBody.equals(lambda.myBody) : lambda.myBody != null) return false;
        if (myDocumentation != null ? !myDocumentation.equals(lambda.myDocumentation) : lambda.myDocumentation != null)
            return false;
        if (myInteractive != null ? !myInteractive.equals(lambda.myInteractive) : lambda.myInteractive != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = myArgumentList != null ? myArgumentList.hashCode() : 0;
        result = 31 * result + (myDocumentation != null ? myDocumentation.hashCode() : 0);
        result = 31 * result + (myInteractive != null ? myInteractive.hashCode() : 0);
        result = 31 * result + (myBody != null ? myBody.hashCode() : 0);
        result = 31 * result + nRequiredArguments;
        result = 31 * result + (infiniteArgs ? 1 : 0);
        result = 31 * result + nKeywords;
        return result;
    }

    @Override
    public LispList getInteractiveForm() {
        return myInteractive == null ? LispList.list() : myInteractive;
    }

    protected final static class LambdaArgument {
        private LispSymbol myKeyword = LispSymbol.ourNil;
        private LispSymbol myVar;
        private LispObject myInitForm = LispSymbol.ourNil;
        private LispSymbol mySetVar = LispSymbol.ourNil;
        public enum Type {REQUIRED, OPTIONAL, REST, KEYWORD}
        private Type myType;

        public LambdaArgument (Type type, LispObject arg, String fName) {
            myType = type;
            if (arg instanceof LispList) {
                List<LispObject> list = ((LispList) arg).toLispObjectList();
                if (myType == Type.KEYWORD && list.get(0) instanceof LispList) {
                    List<LispObject> def = ((LispList) list.get(0)).toLispObjectList();
                    if (def.size() != 2 || !(def.get(0) instanceof LispSymbol) || !(def.get(1) instanceof LispSymbol))
                        throw new InvalidFunctionException(fName);
                    myKeyword = (LispSymbol) def.get(0);
                    myVar = (LispSymbol) def.get(1);
                } else if (list.get(0) instanceof LispSymbol) {
                    myKeyword = new LispSymbol(':'+ ((LispSymbol) list.get(0)).getName());
                    myVar = (LispSymbol) list.get(0);
                } else {
                    throw new InvalidFunctionException(fName);
                }
                if (list.size() > 1) {
                    myInitForm = list.get(1);
                    if (list.size() == 3) {
                        if (list.get(2) instanceof LispSymbol)
                            mySetVar = (LispSymbol) list.get(2);
                        else throw new InvalidFunctionException(fName);
                    } else if (list.size() > 3) throw new InvalidFunctionException(fName);
                }
            } else if (arg instanceof LispSymbol) {
                myVar = (LispSymbol) arg;
            } else
                throw new InvalidFunctionException(fName);
        }

        public Type getType() {
            return myType;
        }

        public LispSymbol getKeyword() {
            if (myType == Type.KEYWORD)
                return myKeyword;
            return null;
        }

        //todo: for test only
        public LispSymbol getVar() {
            return myVar;
        }

        //todo: for test only
        public LispSymbol getSetVar() {
            return mySetVar;
        }

        //todo: for test only
        public LispObject getInitForm() {
            return myInitForm;
        }

        public void setValue (Environment inner, @Nullable LispObject value) {
            myVar = new LispSymbol(myVar.getName());
            if (value == null) {
                myVar.setValue(myInitForm.evaluate(inner));
                if (!mySetVar.equals(LispSymbol.ourNil))
                    mySetVar = new LispSymbol(mySetVar.getName(), LispSymbol.ourNil);
                inner.defineSymbol(myVar);
                return;
            }
            myVar.setValue(value);
            if (!mySetVar.equals(LispSymbol.ourNil))
                mySetVar = new LispSymbol(mySetVar.getName(), LispSymbol.ourT);
            inner.defineSymbol(myVar);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LambdaArgument)) return false;

            LambdaArgument LambdaArgument = (Lambda.LambdaArgument) o;

            if (myInitForm != null ? !myInitForm.equals(LambdaArgument.myInitForm) : LambdaArgument.myInitForm != null)
                return false;
            if (myKeyword != null ? !myKeyword.equals(LambdaArgument.myKeyword) : LambdaArgument.myKeyword != null) return false;
            if (mySetVar != null ? !mySetVar.equals(LambdaArgument.mySetVar) : LambdaArgument.mySetVar != null) return false;
            if (myType != LambdaArgument.myType) return false;
            if (myVar != null ? !myVar.equals(LambdaArgument.myVar) : LambdaArgument.myVar != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = myKeyword != null ? myKeyword.hashCode() : 0;
            result = 31 * result + (myVar != null ? myVar.hashCode() : 0);
            result = 31 * result + (myInitForm != null ? myInitForm.hashCode() : 0);
            result = 31 * result + (mySetVar != null ? mySetVar.hashCode() : 0);
            result = 31 * result + (myType != null ? myType.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return myVar.toString();
        }
    }
}
