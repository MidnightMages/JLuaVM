package dev.asdf00.jluavm.lexer;

import dev.asdf00.jluavm.exceptions.loading.LuaLexerException;
import dev.asdf00.jluavm.parsing.Lexer;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static dev.asdf00.jluavm.Util.expandOptions;
import static org.junit.jupiter.api.Assertions.*;

public class LexerTest {

    private static ArrayList<Token> collectTokens(Lexer lexer) {
        var toks = new ArrayList<Token>();
        while (true) {
            var t = lexer.next();
            toks.add(t);
            if (t.type() == TokenType.EOF) {
                break;
            }
        }
        return toks;
    }

    private static String getStringRep(ArrayList<Token> toks) {
        return toks.stream().map(t -> switch (t.type()) {
            case IDENT -> t.stVal();
            case NUMERAL -> String.valueOf(t.nVal());
            default -> t.type().rep;
        }).collect(Collectors.joining(" "));
    }

    private static ArrayList<Token> collectTokensAssertType(Lexer lexer, String tokens) {
        var toks = collectTokens(lexer);
        var tokString = getStringRep(toks);
        var toksActual = toks.stream().map(t -> t.type().name()).collect(Collectors.joining(";"));
        assertEquals(tokens, toksActual);
        return toks;
    }

    private static ArrayList<Token> lexAssertTokens(String code, String expectedTokens) {
        return collectTokensAssertType(new Lexer(code), expectedTokens);
    }

    private static ArrayList<Token> lexAndCollectTokens(String code) {
        return collectTokens(new Lexer(code));
    }

    @Test
    void simpleSnippet() {
        var lexer = new Lexer("while true\rdo\nprint(\"blabla\") end");
        var toks = collectTokensAssertType(lexer, "WHILE;TRUE;DO;IDENT;LPAR;LITERAL_STRING;RPAR;END;EOF");
    }

    @Test
    void multilineComment() {
        lexAssertTokens("""
                local coroutineResumeOrig = _G.coroutine.resume

                --[[
                
                _G.computer = {}
                _G.computer.sleep = function(duration) -- performs a non-wasteful-sleep
                
                end
                
                _G.computer.getMachineEvents = function() -- returns (string eventName, varargsPacked)[]
                    return {}
                end
                
                _G.computer.waitForMachineEvent = function(timeout) -- returns after timeout or instantly if GetMachineEvents were to return a non-empty table
                
                end
                
                _G.computer.shutdown = function(reboot)
                
                end
                
                _G.computer.uptime = function() -- time since computer boot (not increasing while paused)
                
                end
                ]]""", "LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;DOT;IDENT;EOF");
    }

    @Test
    void complexSnippet() {
        var lexer = new Lexer("""
                local coroutineResumeOrig = _G.coroutine.resume
                
                --[[
                
                _G.computer = {}
                _G.computer.sleep = function(duration) -- performs a non-wasteful-sleep
                
                end
                
                _G.computer.getMachineEvents = function() -- returns (string eventName, varargsPacked)[]
                    return {}
                end
                
                _G.computer.waitForMachineEvent = function(timeout) -- returns after timeout or instantly if GetMachineEvents were to return a non-empty table
                
                end
                
                _G.computer.shutdown = function(reboot)
                
                end
                
                _G.computer.uptime = function() -- time since computer boot (not increasing while paused)
                
                end
                ]]
                
                local coroutineResumedByMap = {} -- Dict<coroutine coroutine, coroutine parent>
                local coroutineExecutionQueue = {}
                local coroutineRescheduleMap = {} -- Dict<coroutine co, number earliestRescheduleTimestamp>
                local coroutineResumptionArguments = {} -- Dict<coroutine co, varargsPacked args>
                local shutdownTasks = {} -- function[]
                local eventQueue = {} -- (string eventName, varargsPacked)[]
                
                local subscribedCoroutineMap = {} -- Dict<string eventname, (coroutine subscribedCoroutine)[]>
                local subscribedCoroutineMap_Inverse = {} -- Dict<coroutine subscribedCoroutine, string eventname>
                local function subscribedCoroutineMap_add(eventKey, newCoroutine)
                    local existing = subscribedCoroutineMap[eventKey]
                    if not existing then
                        subscribedCoroutineMap[eventKey] = {newCoroutine}
                    else
                        table.insert(existing, newCoroutine)
                    end
                    subscribedCoroutineMap_Inverse[newCoroutine] = eventKey
                end
                
                local function subscribedCoroutineMap_removeByKey(eventKey)
                    local existing = subscribedCoroutineMap[eventKey]
                    for i = 1, #existing do
                        subscribedCoroutineMap_Inverse[existing[i]] = nil
                    end
                    subscribedCoroutineMap[eventKey] = nil
                end
                
                local function subscribedCoroutineMap_removeByValue(coroutine)
                    local existingTable = subscribedCoroutineMap[subscribedCoroutineMap_Inverse[coroutine]]
                    for i = 1, #existingTable do
                        if existingTable[i] == coroutine then
                            table.remove(existingTable, i)
                            break
                        end
                    end
                
                    subscribedCoroutineMap_Inverse[coroutine] = nil
                end
                
                local function enqueueCoroutineWithArgs(co, packedVarArgs)
                    coroutineResumptionArguments[co] = packedVarArgs
                    table.insert(coroutineExecutionQueue, co)
                end
                
                local function processEvent(eventTable)
                    local eventName = eventTable[1]
                    local toExecute = subscribedCoroutineMap[eventName]
                    if toExecute then
                        for i = 1, #toExecute do
                            local co = toExecute[i]
                            enqueueCoroutineWithArgs(co, eventTable)
                        end
                        subscribedCoroutineMap_removeByKey(eventName)
                    end
                end
                
                local COROUTINE_WAIT_TYPE = {}
                COROUTINE_WAIT_TYPE.EVENT_OR_TIMEOUT = 1 -- {type}
                COROUTINE_WAIT_TYPE.SUB_YIELDRET = 2  -- {type}
                COROUTINE_WAIT_TYPE.SUPER_RESUMPTION = 3 -- {type}
                
                local coroutineWaitReason = nil
                ---@diagnostic disable-next-line: duplicate-set-field
                _G.coroutine.resume = function(co, ...)
                    coroutineResumedByMap[co] = coroutine.running()
                    coroutineWaitReason = COROUTINE_WAIT_TYPE.SUB_YIELDRET
                    _G.coroutine.yield({co, table.pack(...)})
                end
                
                
                ---@diagnostic disable-next-line: duplicate-set-field
                _G.coroutine.wrap = function(f)
                    local co = _G.coroutine.create(f)
                    return function(...)
                        local packed = table.pack(_G.coroutine.resume(co, ...))
                        if not packed[1] then
                            error("Error during coroutine.wrap: "..tostring(packed[2]))
                        end
                        return table.unpack(packed, 2)
                    end
                end
                
                _G.os = {}
                _G.os.spawnCoroutine = function(func, ...) -- Creates a new coroutine that is managed by the scheduler but does not have a parent, optionally with arguments
                    local co = _G.coroutine.create(func)
                    enqueueCoroutineWithArgs(co, table.pack(...))
                end
                
                _G.event = {}
                event.pull = function(timeout, eventName)
                    if timeout == nil and eventName == nil then
                       error("At least one argument must be non-nil.")
                    end
                
                    coroutineWaitReason = COROUTINE_WAIT_TYPE.EVENT_OR_TIMEOUT
                    return _G.coroutine.yield({timeout, eventName})
                end
                
                event.push = function(eventName, ...)
                    table.insert(eventQueue, {eventName, table.pack(...)})
                end
                
                event.subscribe = function(eventName, func)
                    local unsubscriber = {}
                    if eventName == "shutdown" then
                        function unsubscriber:unsubscribe()
                            for i = #shutdownTasks, 1, -1 do
                                if shutdownTasks[i] == func then
                                    table.remove(shutdownTasks, i)
                                end
                            end
                        end
                        table.insert(shutdownTasks, func)
                    else
                        unsubscriber["unsubscribed"] = false
                        function unsubscriber:unsubscribe() self.unsubscribed = true end
                
                        os.spawnCoroutine(function()
                            while true do
                                local args = table.pack(event.pull(nil, eventName))
                                if unsubscriber["unsubscribed"] ~= false then
                                    break
                                end
                                func(table.unpack(args))
                            end
                        end)
                    end
                    return unsubscriber
                end
                
                
                _G.os.sleep = function(duration)
                    _G.event.pull(duration, nil)
                end
                
                local shutdownOs = false
                local rebootOs = nil
                
                _G.os.shutdown = function(reboot)
                    shutdownOs = true
                    rebootOs = reboot or false
                end
                
                local function printSched(...)
                    print("[Scheduler]",...)
                end
                
                local lastCoroutineStartTime = nil -- TODO add yield hook, and if non nil and greater than the timeout then we do something
                
                local function coroutineScheduler()
                    local i = 1
                    while true do
                        if shutdownOs then
                            printSched("Beginning shutdown")
                            for i = #shutdownTasks, 1, -1 do
                                shutdownTasks[i]()
                            end
                            printSched("Shutdown complete")
                            computer.shutdown(rebootOs)
                        end
                
                        -- check event queues
                        for j = 1, #eventQueue do processEvent(eventQueue[i]) end
                        local machineEvents = computer.getMachineEvents()
                        for j = 1, #machineEvents do processEvent(machineEvents[i]) end
                
                        -- process timeouts
                        local now = computer.uptime()
                        local removed = {}
                        local earliestTimestamp = math.huge
                        for k,v in pairs(coroutineRescheduleMap) do
                            if v < now then
                                table.insert(removed, k)
                                enqueueCoroutineWithArgs(k, nil)
                                subscribedCoroutineMap_removeByValue(k)
                            elseif v < earliestTimestamp then
                                earliestTimestamp = v
                            end
                
                        end
                        for j = 1, #removed do coroutineRescheduleMap[removed[j]] = nil end
                
                        -- efficiently sleep if there are no tasks
                        if #coroutineExecutionQueue == 0 then
                            local waitTime = math.max(earliestTimestamp-now,0)
                            computer.waitForMachineEvent(waitTime)
                            goto continue
                        end
                
                        -- process the next task
                        local nextCo = table.remove(coroutineExecutionQueue, 0)
                        local nextArgs = coroutineResumptionArguments[nextCo]
                        coroutineResumptionArguments[nextCo] = nil
                
                        coroutineWaitReason = COROUTINE_WAIT_TYPE.SUPER_RESUMPTION
                        lastCoroutineStartTime = computer.uptime()
                        local coReturnValue = nil
                        if nextArgs ~= nil then
                            coReturnValue = table.pack(coroutineResumeOrig(nextCo, table.unpack(nextArgs)))
                        else
                            coReturnValue = table.pack(coroutineResumeOrig(nextCo))
                        end
                        lastCoroutineStartTime = nil
                
                        if coroutineWaitReason == COROUTINE_WAIT_TYPE.EVENT_OR_TIMEOUT then
                            assert(coReturnValue[1] == true, "EVENT_OR_TIMEOUT failed")
                            local coData = coReturnValue[2]
                            local timeout = coData[1]
                            local eventName = coData[2]
                
                            if timeout ~= nil then coroutineRescheduleMap[nextCo] = computer.uptime() + timeout end
                            if eventName ~= nil then subscribedCoroutineMap_add(eventName, nextCo) end
                        elseif coroutineWaitReason == COROUTINE_WAIT_TYPE.SUB_YIELDRET then -- coReturnValue: {bool success, {coroutine co to resume, packedVarArgs}}
                            assert(coReturnValue[1] == true, "SUB_YIELDRET failed")
                            local coData = coReturnValue[2]
                            enqueueCoroutineWithArgs(coData[1], coData[2])
                        elseif coroutineWaitReason == COROUTINE_WAIT_TYPE.SUPER_RESUMPTION then
                            local parent = coroutineResumedByMap[nextCo]
                            if parent ~= coroutine.running() then -- coroutine was not started by the main lua coroutine
                                coroutineResumedByMap[nextCo] = nil -- coroutine has no "resumed by" relationship after yielding
                                enqueueCoroutineWithArgs(parent, coReturnValue)
                            else
                                if coReturnValue[1] == false then -- if coroutine has errored and there is no parent coroutine
                                    error("Coroutine errored with reason: "..tostring(coReturnValue[2]))
                                end
                                if coroutine.status(nextCo) == "suspended" then -- a root coroutine has yielded
                                    enqueueCoroutineWithArgs(nextCo, nil)
                                end -- otherwise drop
                            end
                        else
                            error("Not implemented")
                        end
                        ::continue::
                
                        if coroutine.running() ~= nil then -- if not in hypervisor mode
                            coroutine.yield()
                        end
                    end
                    error("All coroutines have exited. Shutdown time?")
                end
                
                
                local function boot()
                
                end
                
                os.spawnCoroutine(boot)
                coroutineScheduler()
                """);
        var toks = collectTokensAssertType(lexer, "LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;DOT;IDENT;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;FUNCTION;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;IF;NOT;IDENT;THEN;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;LBRAC;IDENT;RBRAC;ELSE;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;END;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;IDENT;END;LOCAL;FUNCTION;IDENT;LPAR;IDENT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;IDENT;LBRAK;IDENT;LBRAK;IDENT;RBRAK;RBRAK;ASSIGN;NIL;END;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;NIL;END;LOCAL;FUNCTION;IDENT;LPAR;IDENT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;LBRAK;IDENT;RBRAK;RBRAK;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;IF;IDENT;LBRAK;IDENT;RBRAK;EQ;IDENT;THEN;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;BREAK;END;END;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;NIL;END;LOCAL;FUNCTION;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;IDENT;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;END;LOCAL;FUNCTION;IDENT;LPAR;IDENT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;NUMERAL;RBRAK;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;IF;IDENT;THEN;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;END;IDENT;LPAR;IDENT;RPAR;END;END;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;IDENT;DOT;IDENT;ASSIGN;NUMERAL;IDENT;DOT;IDENT;ASSIGN;NUMERAL;IDENT;DOT;IDENT;ASSIGN;NUMERAL;LOCAL;IDENT;ASSIGN;NIL;IDENT;DOT;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;COMMA;TDOT;RPAR;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;IDENT;DOT;IDENT;LPAR;RPAR;IDENT;ASSIGN;IDENT;DOT;IDENT;IDENT;DOT;IDENT;DOT;IDENT;LPAR;LBRAC;IDENT;COMMA;IDENT;DOT;IDENT;LPAR;TDOT;RPAR;RBRAC;RPAR;END;IDENT;DOT;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;RETURN;FUNCTION;LPAR;TDOT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;DOT;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;TDOT;RPAR;RPAR;IF;NOT;IDENT;LBRAK;NUMERAL;RBRAK;THEN;IDENT;LPAR;LITERAL_STRING;DDOT;IDENT;LPAR;IDENT;LBRAK;NUMERAL;RBRAK;RPAR;RPAR;END;RETURN;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;NUMERAL;RPAR;END;END;IDENT;DOT;IDENT;ASSIGN;LBRAC;RBRAC;IDENT;DOT;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;COMMA;TDOT;RPAR;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;IDENT;LPAR;IDENT;COMMA;IDENT;DOT;IDENT;LPAR;TDOT;RPAR;RPAR;END;IDENT;DOT;IDENT;ASSIGN;LBRAC;RBRAC;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;COMMA;IDENT;RPAR;IF;IDENT;EQ;NIL;AND;IDENT;EQ;NIL;THEN;IDENT;LPAR;LITERAL_STRING;RPAR;END;IDENT;ASSIGN;IDENT;DOT;IDENT;RETURN;IDENT;DOT;IDENT;DOT;IDENT;LPAR;LBRAC;IDENT;COMMA;IDENT;RBRAC;RPAR;END;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;COMMA;TDOT;RPAR;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;LBRAC;IDENT;COMMA;IDENT;DOT;IDENT;LPAR;TDOT;RPAR;RBRAC;RPAR;END;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;COMMA;IDENT;RPAR;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;IF;IDENT;EQ;LITERAL_STRING;THEN;FUNCTION;IDENT;COLON;IDENT;LPAR;RPAR;FOR;IDENT;ASSIGN;HASH;IDENT;COMMA;NUMERAL;COMMA;SUB;NUMERAL;DO;IF;IDENT;LBRAK;IDENT;RBRAK;EQ;IDENT;THEN;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;END;END;END;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;ELSE;IDENT;LBRAK;LITERAL_STRING;RBRAK;ASSIGN;FALSE;FUNCTION;IDENT;COLON;IDENT;LPAR;RPAR;IDENT;DOT;IDENT;ASSIGN;TRUE;END;IDENT;DOT;IDENT;LPAR;FUNCTION;LPAR;RPAR;WHILE;TRUE;DO;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;DOT;IDENT;LPAR;NIL;COMMA;IDENT;RPAR;RPAR;IF;IDENT;LBRAK;LITERAL_STRING;RBRAK;NE;FALSE;THEN;BREAK;END;IDENT;LPAR;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;RPAR;END;END;RPAR;END;RETURN;IDENT;END;IDENT;DOT;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;RPAR;IDENT;DOT;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;NIL;RPAR;END;LOCAL;IDENT;ASSIGN;FALSE;LOCAL;IDENT;ASSIGN;NIL;IDENT;DOT;IDENT;DOT;IDENT;ASSIGN;FUNCTION;LPAR;IDENT;RPAR;IDENT;ASSIGN;TRUE;IDENT;ASSIGN;IDENT;OR;FALSE;END;LOCAL;FUNCTION;IDENT;LPAR;TDOT;RPAR;IDENT;LPAR;LITERAL_STRING;COMMA;TDOT;RPAR;END;LOCAL;IDENT;ASSIGN;NIL;LOCAL;FUNCTION;IDENT;LPAR;RPAR;LOCAL;IDENT;ASSIGN;NUMERAL;WHILE;TRUE;DO;IF;IDENT;THEN;IDENT;LPAR;LITERAL_STRING;RPAR;FOR;IDENT;ASSIGN;HASH;IDENT;COMMA;NUMERAL;COMMA;SUB;NUMERAL;DO;IDENT;LBRAK;IDENT;RBRAK;LPAR;RPAR;END;IDENT;LPAR;LITERAL_STRING;RPAR;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;END;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;IDENT;LPAR;IDENT;LBRAK;IDENT;RBRAK;RPAR;END;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;RPAR;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;IDENT;LPAR;IDENT;LBRAK;IDENT;RBRAK;RPAR;END;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;RPAR;LOCAL;IDENT;ASSIGN;LBRAC;RBRAC;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;FOR;IDENT;COMMA;IDENT;IN;IDENT;LPAR;IDENT;RPAR;DO;IF;IDENT;LT;IDENT;THEN;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;IDENT;LPAR;IDENT;COMMA;NIL;RPAR;IDENT;LPAR;IDENT;RPAR;ELSEIF;IDENT;LT;IDENT;THEN;IDENT;ASSIGN;IDENT;END;END;FOR;IDENT;ASSIGN;NUMERAL;COMMA;HASH;IDENT;DO;IDENT;LBRAK;IDENT;LBRAK;IDENT;RBRAK;RBRAK;ASSIGN;NIL;END;IF;HASH;IDENT;EQ;NUMERAL;THEN;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;SUB;IDENT;COMMA;NUMERAL;RPAR;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;GOTO;IDENT;END;LOCAL;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;COMMA;NUMERAL;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;NIL;IDENT;ASSIGN;IDENT;DOT;IDENT;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;RPAR;LOCAL;IDENT;ASSIGN;NIL;IF;IDENT;NE;NIL;THEN;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;LPAR;IDENT;COMMA;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;RPAR;RPAR;ELSE;IDENT;ASSIGN;IDENT;DOT;IDENT;LPAR;IDENT;LPAR;IDENT;RPAR;RPAR;END;IDENT;ASSIGN;NIL;IF;IDENT;EQ;IDENT;DOT;IDENT;THEN;IDENT;LPAR;IDENT;LBRAK;NUMERAL;RBRAK;EQ;TRUE;COMMA;LITERAL_STRING;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;NUMERAL;RBRAK;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;NUMERAL;RBRAK;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;NUMERAL;RBRAK;IF;IDENT;NE;NIL;THEN;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;IDENT;DOT;IDENT;LPAR;RPAR;ADD;IDENT;END;IF;IDENT;NE;NIL;THEN;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;END;ELSEIF;IDENT;EQ;IDENT;DOT;IDENT;THEN;IDENT;LPAR;IDENT;LBRAK;NUMERAL;RBRAK;EQ;TRUE;COMMA;LITERAL_STRING;RPAR;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;NUMERAL;RBRAK;IDENT;LPAR;IDENT;LBRAK;NUMERAL;RBRAK;COMMA;IDENT;LBRAK;NUMERAL;RBRAK;RPAR;ELSEIF;IDENT;EQ;IDENT;DOT;IDENT;THEN;LOCAL;IDENT;ASSIGN;IDENT;LBRAK;IDENT;RBRAK;IF;IDENT;NE;IDENT;DOT;IDENT;LPAR;RPAR;THEN;IDENT;LBRAK;IDENT;RBRAK;ASSIGN;NIL;IDENT;LPAR;IDENT;COMMA;IDENT;RPAR;ELSE;IF;IDENT;LBRAK;NUMERAL;RBRAK;EQ;FALSE;THEN;IDENT;LPAR;LITERAL_STRING;DDOT;IDENT;LPAR;IDENT;LBRAK;NUMERAL;RBRAK;RPAR;RPAR;END;IF;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;EQ;LITERAL_STRING;THEN;IDENT;LPAR;IDENT;COMMA;NIL;RPAR;END;END;ELSE;IDENT;LPAR;LITERAL_STRING;RPAR;END;DCOLON;IDENT;DCOLON;IF;IDENT;DOT;IDENT;LPAR;RPAR;NE;NIL;THEN;IDENT;DOT;IDENT;LPAR;RPAR;END;END;IDENT;LPAR;LITERAL_STRING;RPAR;END;LOCAL;FUNCTION;IDENT;LPAR;RPAR;END;IDENT;DOT;IDENT;LPAR;IDENT;RPAR;IDENT;LPAR;RPAR;EOF");
    }

    @Test
    void numeralFails() {
        var number = "1xCAFE;1.5e;0x;0x.;0x1f.p;34.p3".split(";");
        for (var t : number) {
            assertThrows(LuaLexerException.class, () -> {
                var l = new Lexer("local a ={%s}=={%s}\nb = a +{%s}*2".formatted(t, t, t));
                collectTokens(l);
            });
        }
    }

    @Test
    void weirdNumerals() {
        var number = "0xffd.2;0xffd.2p3;0xffd.2e2;0x03.2p4;3.3e5;0x45d;36;3.2e-4;0x0.1e".split(";");
        for (var t : number) {
            assertDoesNotThrow(() -> {
                var l = new Lexer("local a ={%s}=={%s}\nb = a +{%s}*2".formatted(t, t, t));
                collectTokens(l);
            });
        }
    }

    @Test
    void binUnOps() {
        var snippet = """
                    local a = true or false and not (true~ false)
                    if a == (function() return true and a or "teststring" end)() then
                        for i in function() return nil end do
                            print(a)
                        end
                    end
                """;

        lexAssertTokens(snippet, "LOCAL;IDENT;ASSIGN;TRUE;OR;FALSE;AND;NOT;LPAR;TRUE;BXOR;FALSE;RPAR;IF;IDENT;EQ;LPAR;FUNCTION;LPAR;RPAR;RETURN;TRUE;AND;IDENT;OR;LITERAL_STRING;END;RPAR;LPAR;RPAR;THEN;FOR;IDENT;IN;FUNCTION;LPAR;RPAR;RETURN;NIL;END;DO;IDENT;LPAR;IDENT;RPAR;END;END;EOF");
    }

    @Test
    void stringTest() {
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = \"\n\""));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = \""));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = '"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = '\n'"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = 2'\n"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = 2'"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("local a = 1 for'"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("return'"));
        assertDoesNotThrow(() -> lexAndCollectTokens("for a in ' do print(') end"));
        assertDoesNotThrow(() -> lexAndCollectTokens("for a in \" do print(\") end"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("for a in ' do print(\") end"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("for a in \" do print(') end"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("for a in b:c('\n\"') end"));
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("for a in b:c('\"\n') end"));
        assertDoesNotThrow(() -> lexAndCollectTokens("for a in b:c('\"\\n') end"));
        assertDoesNotThrow(() -> lexAndCollectTokens("for a in b:c('\\n\"') end"));

        var validEscSeqs = "abfnrtvz0123456789\\\"'".toCharArray();
        for (var l : validEscSeqs) {
            assertDoesNotThrow(() -> lexAndCollectTokens("a = '\\" + l + "'"));
        }

        for (var l : "abcdefghijklmnopqrstuvwxyz0123456789,.-#+;:_+*~[]()".toCharArray()) {
            boolean ok = true;
            for (char validEscSeq : validEscSeqs) {
                if (validEscSeq == l) {
                    ok = false;
                    break;
                }
            }
            if (ok)
                assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("a = '\\" + l + "'"), "Char was: " + l);
        }
        var toks = lexAssertTokens("a = '\\1234'", "IDENT;ASSIGN;LITERAL_STRING;EOF");
        assertEquals("{4", toks.get(2).stVal());

        var toks2 = lexAssertTokens("a = '\\xAA \\u{57}'", "IDENT;ASSIGN;LITERAL_STRING;EOF");
    }

    @Test
    void invalidIdentifier() {
        assertThrows(LuaLexerException.class, () -> lexAndCollectTokens("$ = 1"));
    }

    @Test
    void invalidStringMarkers() {
        var snippets = new String[]{
                "local a = [=§a|=a|==a|,|;|-§[abcef]]",
        };
        for (var s : expandOptions(snippets)) {
            assertThrows(LuaLexerException.class, () -> lexAndCollectTokens(s), ()->"Code was "+s );
        }
    }

    @Test
    void funnyHexNumber() {
        // LUAC DEVIATION. Our precision seems to be 2 digits higher
        var toks = lexAndCollectTokens("0X1.921FB54442D18P+1"); // = 3.1415926535898
        assertEquals(toks.get(0).type(), TokenType.NUMERAL);
        assertEquals(3.141592653589793, toks.get(0).nVal());
        assertEquals(toks.get(1).type(), TokenType.EOF);
    }

    @Test
    void expNumber() {
        var toks = lexAndCollectTokens("1e2");
        assertEquals(toks.get(0).type(), TokenType.NUMERAL);
        assertEquals(100, toks.get(0).nVal());
        assertEquals(toks.get(1).type(), TokenType.EOF);
    }

    @Test
    void simpleCall() {
        var test = lexAndCollectTokens("""
                print("hi")
                -- some comment
                """);
        int a = 0;
    }
}
