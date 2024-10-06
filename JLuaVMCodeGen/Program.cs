using System.Reflection;
using System.Text.RegularExpressions;

namespace JLuaVMCodeGen;

internal partial class Program {

    private static string jluavmRootDir = Assembly.GetExecutingAssembly().Location.Split(Path.DirectorySeparatorChar)[..^5].Aggregate((a, b) => $"{a}{Path.DirectorySeparatorChar}{b}");

    private static void GenFile(string @namespace, Func<string> contents) {
        Console.WriteLine($"Generating file {@namespace.Split('.')[^1]}$$.java");
        File.WriteAllText(Path.Combine(jluavmRootDir, "src", @namespace.Replace('.', Path.DirectorySeparatorChar)) + "$$.java", $"package {@namespace[..@namespace.LastIndexOf('.')]};\n" + contents.Invoke());
    }

    private static string GetBinaryOperationSnippetXY(string opName, string directCode, CoercionType coercionMethod) {
        var rv = "";
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
            throw new LuaTypeError("attempted to perform operation '%s {{opName}} %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
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
            throw new LuaTypeError("attempted to perform operation 'len %s'".formatted(x.getType().fancyName));
        }
""";
            case "_builtin_not":
                return "        return new LuaBoolean$(x.isNil() || x.isBoolean() && !((LuaBoolean$)x).getValue());";
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

            throw new LuaTypeError("attempted to perform operation '{{opName}} %s'".formatted(x.getType().fancyName));            
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

public class BinaryOpNode$$ extends Node {
    protected final Node x;
    protected final Node y;
    private final TokenType tokenType;

    public BinaryOpNode$$(Node x, Node y, TokenType tokenType) {
        this.x = x;
        this.y = y;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("BinaryOpNode_RTIMPL$$.IL__%s(%s, %s)".formatted(Objects.requireNonNull(tokenType.metatableFuncNameBinary), x.generate(), y.generate()));
    }
}
""";
        });
        GenFile("dev.asdf00.jluavm.rtutils.BinaryOpNode_RTIMPL", () => $$"""
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

    public static LuaVariable$ IL__{{kv.FuncName}}(LuaVariable$ x, LuaVariable$ y) {
{{GetBinaryOperationSnippetXY(kv.FuncName, kv.DirectSnippet, kv.CoercionKind)}}
    }
""").Aggregate((a, b) => a + "\n" + b);

        GenFile("dev.asdf00.jluavm.parsing.ir.operations.UnaryOpNode", () => {

            return $$"""

import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Objects;

public class UnaryOpNode$$ extends Node {
    protected final Node x;
    private final TokenType tokenType;

    public UnaryOpNode$$(Node x, TokenType tokenType) {
        this.x = x;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("UnaryOpNode_RTIMPL$$.IL__%s(%s)".formatted(Objects.requireNonNull(tokenType.metatableFuncNameUnary), x.generate()));
    }
}
""";
        });
        GenFile("dev.asdf00.jluavm.rtutils.UnaryOpNode_RTIMPL", () => $$"""

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
{{GetGeneratedUnaryBodies()}}
}
""");

        string GetGeneratedUnaryBodies() => unaryOperations.Select((kv) => $$"""

    public static LuaVariable$ IL__{{kv.FuncName}}(LuaVariable$ x) {
{{GetUnaryOperationSnippet(kv.FuncName, kv.DirectSnippet, kv.CoercionKind)}}
    }
""").Aggregate((a, b) => a + "\n" + b);
    }




    [GeneratedRegex("//.*?$")]
    private static partial Regex EndOfLineComments();
}

internal record struct MethodDef(string FuncName, string DirectSnippet, CoercionType CoercionKind) { }