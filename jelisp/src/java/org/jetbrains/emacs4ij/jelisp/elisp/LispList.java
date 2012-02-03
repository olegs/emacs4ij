package org.jetbrains.emacs4ij.jelisp.elisp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.emacs4ij.jelisp.Environment;
import org.jetbrains.emacs4ij.jelisp.GlobalEnvironment;
import org.jetbrains.emacs4ij.jelisp.exception.InvalidFunctionException;
import org.jetbrains.emacs4ij.jelisp.exception.VoidFunctionException;
import org.jetbrains.emacs4ij.jelisp.exception.WrongTypeArgumentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ekaterina.polishchuk
 * Date: 7/8/11
 * Time: 1:30 PM
 * To change this template use File | Settings | File Templates.
 *
 * this class is a lisp list = (something in brackets 5 5 delimited by spaces or line breaks)
 */
public class LispList extends LispObject {
    private LObject myCar = null;
    private LObject myCdr = null;
    
    private Boolean isTrueList;

    public static LispList list (LObject ... objects) {
        return new LispList(new ArrayList<LObject>(Arrays.asList(objects)));
    }

    public static LispList list (List<LObject> data) {
        return new LispList(data);
    }

    public static LispList testList (LObject ... objects) {
        return new LispList(new ArrayList<LObject>(Arrays.asList(objects)), true);
    }

    public static LispList cons (LObject car, LObject cdr) {
        return new LispList(car, cdr);
    }

    private LispList (List<LObject> data, boolean test) {
        if (data == null || data.size() == 0) {
            isTrueList = true;
            myCar = LispSymbol.ourNil;
            myCdr = LispSymbol.ourNil;
            return;
        }

        if (data.get(0) == null) {
            throw new RuntimeException("Null element in LispList!");
        }

        myCar = data.get(0);
        if (data.size() == 2) {
            isTrueList = false;
            myCdr = data.get(1);
            return;
        }
        if (data.size() == 1) {
            isTrueList = false;
            myCdr = null;
            return;
        }

        isTrueList = true;
        myCdr = new LispList(data.subList(1, data.size()), true);
    }

    private LispList (List<LObject> data) {
        isTrueList = true;
        if (data == null || data.size() == 0) {
            myCar = LispSymbol.ourNil;
            myCdr = LispSymbol.ourNil;
            return;
        }

        if (data.get(0) == null) {
            throw new RuntimeException("Null element in LispList!");
        }

        myCar = data.get(0);
        if (data.size() == 1) {
            myCdr = LispSymbol.ourNil;
            return;
        }
        myCdr = new LispList(data.subList(1, data.size()));
    }

    private LispList (@NotNull LObject car, @NotNull LObject cdr) {
        myCar = car;
        myCdr = cdr;
        isTrueList = false;
    }

    public boolean isTrueList() {
        return isTrueList;
    }

    public boolean isEmpty() {
        return (myCar.equals(LispSymbol.ourNil) && myCdr.equals(LispSymbol.ourNil));
    }

    /**
     * @param environment@return the result of last function execution
     */
    @Override
    public LObject evaluate(Environment environment) {
        if (isEmpty())
            return LispSymbol.ourNil;
        //todo: if assotiated list?

        LispSymbol fun;
        try {
            fun = (LispSymbol)car();
        } catch (ClassCastException e) {
            throw new InvalidFunctionException(car().toString());
        }
        LispSymbol symbol = GlobalEnvironment.INSTANCE.find(fun.getName());
        if (symbol == null || !symbol.isFunction()) {
            //while we are not loading all elisp code, perform search on request
            System.out.println("upload " + fun.getName());
            try {
                symbol = GlobalEnvironment.INSTANCE.findAndRegisterEmacsFunctionOrMacro(fun);
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                throw new VoidFunctionException(fun.getName());
            }
            if (symbol == null || !symbol.isFunction())
                throw new VoidFunctionException(fun.getName());
            System.out.println("uploaded " + fun.getName());
        }
        List<LObject> data = myCdr instanceof LispList ? ((LispList)myCdr).toLObjectList() : new ArrayList<LObject>();
        return symbol.evaluateFunction(environment, data);
    }

    public List<LObject> toLObjectList() {
        ArrayList<LObject> list = new ArrayList<>();
       /* if (!isTrueList) {
            list.add(myCar);
            list.add(myCdr);
            return list;
        } */
       // if (isEmpty()) return new ArrayList<LObject>();
        LObject cell = this;
        do {
            LObject cdr = ((LispList)cell).cdr();
            if (cdr instanceof LispList || ((LispList)cell).isTrueList())
                list.add(((LispList)cell).car());
            else {
                list.add(cell);
                break;
            }
            cell = cdr;
        } while (cell instanceof LispList);
        return list;
    }

    private boolean isNil (LObject object) {
        return (object instanceof LispSymbol && ((LispSymbol) object).getName().equals("nil"));
    }
    
    @Override
    public String toString() {
        return toString(true);
    }
    
    private String toString (boolean drawBrackets) {
        List<LObject> objectList = toLObjectList();
        if (objectList.isEmpty())
            return "nil";
        if (objectList.size() == 1 && objectList.get(0).equals(LispSymbol.ourNil))
            return "nil";
        String list = drawBrackets ? "(" : "";
        if (isTrueList) {
            for (int i = 0; i != objectList.size(); ++i) {
                list += objectList.get(i).toString() + " ";
            }
            return list.trim() + (drawBrackets ? ")" : "");
        } else {
            if (isNil(myCar) && isNil(myCdr))
                list += "nil" + (drawBrackets ? ")" : "");
            else if (isNil(myCdr))
                list += myCar.toString() + (drawBrackets ? ")" : "");
            else {
                list += myCar.toString();
                if (myCdr instanceof LispList)
                    list += " " + ((LispList)myCdr).toString(false) + (drawBrackets ? ")" : "");
                else
                    list += " . " + myCdr.toString() + (drawBrackets ? ")" : "");
            }
            return list;
        }
        
    }

    public LObject car () {
        return myCar;
    }

    public LObject cdr () {
        return myCdr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LispList)) {
            return o == LispSymbol.ourNil && isEmpty();
        }

        LispList lispList = (LispList) o;

        if (myCar != null ? !myCar.equals(lispList.myCar) : lispList.myCar != null) return false;
        if (myCdr != null ? !myCdr.equals(lispList.myCdr) : lispList.myCdr != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = myCar != null ? myCar.hashCode() : 0;
        result = 31 * result + (myCdr != null ? myCdr.hashCode() : 0);
        return result;
    }

    public LispObject memq (LObject element) {
        if (!isTrueList) {
            throw new RuntimeException("wrong usage??");
        }
        for (LObject cdr = this; cdr != LispSymbol.ourNil; cdr = ((LispList)cdr).cdr()) {
            if (((LispList)cdr).car().equals(element)) {
                return (LispList)cdr;
            }
        }
        return LispSymbol.ourNil;
    }

    public void setCar (LObject car) {
        myCar = car;
    }
    
    public void setCdr (LObject cdr) {
        myCdr = cdr;
    }
        
    public LObject nReverse () {
        if (isEmpty())
            return this;
        LObject prev = LispSymbol.ourNil;
        LObject tail = this;
        while (tail != LispSymbol.ourNil) {
            if (!(tail instanceof LispList))
                throw new WrongTypeArgumentException("listp", toString());
            LObject next = ((LispList)tail).cdr();
            ((LispList) tail).setCdr(prev);
            prev = tail;
            tail = next;
        }
        return prev;
    }
    
    public void append (LObject object) {
        if (myCdr != null && myCdr instanceof LispList)
            ((LispList) myCdr).append(object);
        else {
            myCdr = object;
            if (!(object instanceof LispList))
                isTrueList = false;
        }
    }
}
