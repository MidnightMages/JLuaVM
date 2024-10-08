using System.Reflection;
using System.Text.RegularExpressions;

namespace JLuaVMCodeGen;

internal partial class Program {

    private static string jluavmRootDir = Assembly.GetExecutingAssembly().Location.Split(Path.DirectorySeparatorChar)[..^5].Aggregate((a, b) => $"{a}{Path.DirectorySeparatorChar}{b}");

    private static void GenFileRT(string @namespace, Func<string> contents) => GenFile(@namespace+"$$", contents);
	private static void GenFile(string @namespace, Func<string> contents) {
        Console.WriteLine($"Generating file {@namespace.Split('.')[^1]}.java");
        File.WriteAllText(Path.Combine(jluavmRootDir, "src", @namespace.Replace('.', Path.DirectorySeparatorChar)) + ".java", $"package {@namespace[..@namespace.LastIndexOf('.')]};\n" + contents.Invoke());
    }

    private static string GetBinaryOperationSnippetXY(string opName, string directCode, CoercionType coercionMethod) {
        var rv = "";

        switch (opName) {
            case "_builtin_and":
                return "        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? y : x;";
            case "_builtin_or":
                return "        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? x : y;";
            case "lt":
            case "le":
                return $$"""
        if (x.getType() == y.getType()){
            if (x.isString())
                return LuaBoolean$.fromState(((LuaString$) x).{{opName}}(((LuaString$) y)));
            if (x.isNumber()) { // TODO make work for NumberBw
                return LuaBoolean$.fromState(((LuaNumber$) x).{{opName}}(((LuaNumber$) y)));
            }
        }
        var mtf = x.isTable() ? ((LuaTable$) x).getMtFunc("__{{opName}}") : null;
        if(mtf == null)
            mtf = y.isTable() ? ((LuaTable$) y).getMtFunc("__{{opName}}") : null;
        if(mtf == null)
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s {{opName}} %s' and could not find any metatable".formatted(x.getType().fancyName, y.getType().fancyName)));

        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x,y)[0]);
""";
            case "eq":
                return """
        if (x.getType() == y.getType()) {
            if (x == y)
                return LuaBoolean$.TRUE;
            if (x.isString()) { // y is also a string
                return LuaBoolean$.fromState(((LuaString$) x).strEquals((LuaString$) y));
            } else if (x.isNumber()) {
                return LuaBoolean$.fromState(((LuaNumber$) x).numEquals((LuaNumber$) y));
            } else if (x.isNumberBw()) {
                return LuaBoolean$.fromState(((LuaNumberBw$) x).numBwEquals((LuaNumberBw$) y));
            } else if (x.isTable()) {
                var mtf = ((LuaTable$) x).getMtFunc("__eq");
                if (mtf == null)
                    mtf = ((LuaTable$) y).getMtFunc("__eq");
                return mtf == null ? LuaBoolean$.FALSE : UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x, y)[0]);
            }
            // remaining types are ref compares and would be handled by the ref equals check above
        }
        return LuaBoolean$.FALSE;
""";
            default:
                break;
        }


        var requiredType = coercionMethod switch {
            CoercionType.ToNum => "Number",
            CoercionType.ToStr => "String",
            CoercionType.ToBitwise => "NumberBw",
            _ => throw new NotImplementedException()
        };
        // step1: try coercion
        if (coercionMethod != CoercionType.None) {
            foreach (var varname in "xy") {
                rv += $$"""
        {{varname}} = IL___COERCETo{{coercionMethod switch { CoercionType.ToNum => "Num", CoercionType.ToStr => "Str", CoercionType.ToBitwise => "Bw", _ => throw new NotImplementedException() }}}({{varname}});

""";
            }
        }
        // step2: if x or y isnt of the target type, look for a metatable
        rv += $$"""        
        if (!x.is{{requiredType}}() || !y.is{{requiredType}}()) { // if any of the args isnt of the required type after coercion, look for a metatable

""";
        foreach (var varname in "xy") {
            rv += $$"""
            if ({{varname}}.isTable()){
                var f = ((LuaTable$) {{varname}}).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }

""";
        }
        rv += $$"""
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s {{opName}} %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof Lua{{requiredType}}$;
        assert y instanceof Lua{{requiredType}}$;
        return {{directCode.TrimEnd(';')}};
""";
        return EndOfLineComments().Replace(rv, string.Empty);
    }

    private static string GetUnaryOperationSnippet(string opName, string directCode, CoercionType coercionMethod, string? nonMetatableTypeOverride = null) {
        var rv = "";

        switch (opName) {
            case "len":
                return """
        if (x.isString()){
            return new LuaNumber$(((LuaString$)x).getLength());
        } else if (x.isTable()) {
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("__len");
            return f != null ? f.Invoke(x)[0] : tbl.getLength();
        } else {
            vm.yeet(new LuaTypeError$("attempted to perform operation 'len %s'".formatted(x.getType().fancyName)));
            throw new RuntimeException("should not be reached");
        }
""";
            case "_builtin_not":
                return "        return IL___builtin_IS_TRUTHY(x).negated();";
            default:
                break;
        }


        var requiredType = (coercionMethod switch {
            CoercionType.ToNum => "Number",
            CoercionType.ToStr => "String",
            CoercionType.ToBitwise => "NumberBw",
            _ => throw new NotImplementedException()
        });
        // step1: try coercion
        if (coercionMethod != CoercionType.None) {
            rv += $$"""
        x = IL___COERCETo{{coercionMethod switch { CoercionType.ToNum => "Num", CoercionType.ToStr => "Str", CoercionType.ToBitwise => "Bw", _ => throw new NotImplementedException() }}}(x);

""";
        }
        // step2: if x isnt of the target type, look for a metatable
        rv += $$"""        
        if (!x.is{{requiredType}}()) { // if the arg isnt of the required type after coercion, look for a metatable

""";
        rv += $$"""
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x)[0]; // metamethods can only return one value
                }
            }
""";
        rv += $$"""

            vm.yeet(new LuaTypeError$("attempted to perform operation '{{opName}} %s'".formatted(x.getType().fancyName)));            
        }
        assert x instanceof Lua{{requiredType}}$;
        return {{directCode.TrimEnd(';')}};
""";
        return EndOfLineComments().Replace(rv, string.Empty);
    }

    private static void Main(string[] args) {

        // reference https://www.lua.org/manual/5.4/manual.html#2.4
        string FCallNumNum(string funcName) => $"((LuaNumber$) x).{funcName}((LuaNumber$) y)";
        string FCallBwBw(string funcName) => $"((LuaNumberBw$) x).{funcName}((LuaNumberBw$) y)";
        string FCallStrStr(string funcName) => $"((LuaString$) x).{funcName}((LuaString$) y)";
        var binaryOperations = new List<MethodDef>() {
            new("_builtin_or", FCallNumNum(""), CoercionType.None),
            new("_builtin_and", FCallNumNum(""), CoercionType.None),

            new("lt", FCallNumNum(""), CoercionType.None),
            new("le", FCallNumNum(""), CoercionType.None),
            new("eq", FCallNumNum(""), CoercionType.None),

            new("bor", FCallBwBw("bor"), CoercionType.ToBitwise),

            new("bxor", FCallBwBw("bxor"), CoercionType.ToBitwise),

            new("band", FCallBwBw("band"), CoercionType.ToBitwise),

            new("shl", FCallBwBw("shl"), CoercionType.ToBitwise),
            new("shr", FCallBwBw("shr"), CoercionType.ToBitwise),

            new("concat", FCallStrStr("concat"), CoercionType.ToStr),

            new("add", FCallNumNum("add"), CoercionType.ToNum),
            new("sub", FCallNumNum("sub"), CoercionType.ToNum),

            new("mul", FCallNumNum("mul"), CoercionType.ToNum),
            new("div", FCallNumNum("div"), CoercionType.ToNum),
            new("idiv", FCallNumNum("idiv"), CoercionType.ToNum),
            new("mod", FCallNumNum("mod"), CoercionType.ToNum),


            new("pow", FCallNumNum("pow"), CoercionType.ToNum),
        };

        string FCallNum(string funcName) => $"((LuaNumber$) x).{funcName}()";
        string FCallBw(string funcName) => $"((LuaNumberBw$) x).{funcName}()";
        var unaryOperations = new List<MethodDef>() {
            new("_builtin_not", FCallNum(""), CoercionType.None),
            new("len", FCallNum(""), CoercionType.None),
            new("unm", FCallNum("unm"), CoercionType.ToNum),
            new("bnot", FCallBw("bnot"), CoercionType.ToBitwise),
        };


        GenFile("dev.asdf00.jluavm.parsing.ir.operations.BinaryOpNode", () => {

            return $$"""

import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Objects;

/** AUTOGENERATED **/
public class BinaryOpNode extends Node {
    protected final Node x;
    protected final Node y;
    private final TokenType tokenType;

    public BinaryOpNode(Node x, Node y, TokenType tokenType) {
        this.x = x;
        this.y = y;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("BinaryOpNode_RTIMPL$$.IL__%s($vm, %s, %s)".formatted(Objects.requireNonNull(tokenType.metatableFuncNameBinary), x.generate(), y.generate()));
    }
}
""";
        });
        GenFileRT("dev.asdf00.jluavm.rtutils.BinaryOpNode_RTIMPL", () => $$"""

import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class BinaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return a; // TODO return a LuaNumber$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariable$ IL___COERCEToBw(LuaVariable$ a){
        return a; // TODO return a LuaNumberBw$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return a; // TODO return a LuaString$ if coercion is possible, otherwise return the argument a
    }
{{GetGeneratedBinaryBodies()}}
}
""");

        string GetGeneratedBinaryBodies() => binaryOperations.Select((kv) => $$"""

    public static LuaVariable$ IL__{{kv.FuncName}}(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
{{GetBinaryOperationSnippetXY(kv.FuncName, kv.DirectSnippet, kv.CoercionKind)}}
    }
""").Aggregate((a, b) => a + "\n" + b);

        GenFile("dev.asdf00.jluavm.parsing.ir.operations.UnaryOpNode", () => {

            return $$"""

import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Objects;

/** AUTOGENERATED **/
public class UnaryOpNode extends Node {
    protected final Node x;
    private final TokenType tokenType;

    public UnaryOpNode(Node x, TokenType tokenType) {
        this.x = x;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("UnaryOpNode_RTIMPL$$.IL__%s($vm, %s)".formatted(Objects.requireNonNull(tokenType.metatableFuncNameUnary), x.generate()));
    }
}
""";
        });
        GenFileRT("dev.asdf00.jluavm.rtutils.UnaryOpNode_RTIMPL", () => $$"""

import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class UnaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToNum(a);
    }
    public static LuaVariable$ IL___COERCEToBw(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToBw(a);
    }
    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToStr(a);
    }
    public static LuaBoolean$ IL___builtin_IS_TRUTHY(LuaVariable$ x) {
        return (x.isNil() || x.isBoolean() && !((LuaBoolean$)x).getValue()) ? LuaBoolean$.FALSE : LuaBoolean$.TRUE;
    }
{{GetGeneratedUnaryBodies()}}
}
""");

        string GetGeneratedUnaryBodies() => unaryOperations.Select((kv) => $$"""

    public static LuaVariable$ IL__{{kv.FuncName}}(LuaVM_RT$ vm, LuaVariable$ x) {
{{GetUnaryOperationSnippet(kv.FuncName, kv.DirectSnippet, kv.CoercionKind)}}
    }
""").Aggregate((a, b) => a + "\n" + b);
    }




    [GeneratedRegex("//.*?$")]
    private static partial Regex EndOfLineComments();
}

internal record struct MethodDef(string FuncName, string DirectSnippet, CoercionType CoercionKind) { }