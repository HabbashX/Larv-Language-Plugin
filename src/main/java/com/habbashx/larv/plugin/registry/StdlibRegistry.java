package com.habbashx.larv.plugin.registry;

import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * Shared stdlib registry used by both completion and inspection.
 * Builtins are always available; other libs require an import.
 */
public final class StdlibRegistry {

    private StdlibRegistry() {}

    public record StdMethod(String name, String signature, String returnType, String description) {}

    /** Builtins that are always available — no import required. */
    public static final Set<String> BUILTINS = Set.of("print", "printErr", "input", "len", "range");

    public static final Map<String, List<StdMethod>> LIBRARIES = new LinkedHashMap<>();

    static {
        LIBRARIES.put("core", List.of(
                new StdMethod("print",    "(value: any)",                    "void",  "Print to stdout"),
                new StdMethod("printErr", "(message: string)",               "void",  "Print to stderr"),
                new StdMethod("input",    "(prompt: string)",                "string","Read line from stdin"),
                new StdMethod("len",      "(collection: any)",               "int",   "Length of collection"),
                new StdMethod("range",    "(end: int)",                      "list",  "Generate range [0..end)")
        ));

        LIBRARIES.put("math", List.of(
                new StdMethod("sqrt",       "(x: number)",                   "number","Square root"),
                new StdMethod("pow",        "(base: number, exp: number)",   "number","Power"),
                new StdMethod("abs",        "(x: number)",                   "number","Absolute value"),
                new StdMethod("floor",      "(x: number)",                   "number","Floor"),
                new StdMethod("ceil",       "(x: number)",                   "number","Ceiling"),
                new StdMethod("round",      "(x: number)",                   "number","Round"),
                new StdMethod("max",        "(a: number, b: number)",        "number","Maximum"),
                new StdMethod("min",        "(a: number, b: number)",        "number","Minimum"),
                new StdMethod("log",        "(x: number)",                   "number","Natural log"),
                new StdMethod("log10",      "(x: number)",                   "number","Base-10 log"),
                new StdMethod("sin",        "(x: number)",                   "number","Sine"),
                new StdMethod("cos",        "(x: number)",                   "number","Cosine"),
                new StdMethod("tan",        "(x: number)",                   "number","Tangent"),
                new StdMethod("asin",       "(x: number)",                   "number","Arc sine"),
                new StdMethod("acos",       "(x: number)",                   "number","Arc cosine"),
                new StdMethod("atan",       "(x: number)",                   "number","Arc tangent"),
                new StdMethod("atan2",      "(y: number, x: number)",        "number","Arc tangent (2-arg)"),
                new StdMethod("toRadians",  "(degrees: number)",             "number","Degrees to radians"),
                new StdMethod("toDegrees",  "(radians: number)",             "number","Radians to degrees"),
                new StdMethod("random",     "()",                            "number","Random [0,1)"),
                new StdMethod("randomInt",  "(min: int, max: int)",          "int",   "Random int in range"),
                new StdMethod("clamp",      "(value: number, min: number, max: number)", "number","Clamp value"),
                new StdMethod("sign",       "(x: number)",                   "number","Sign of x"),
                new StdMethod("pi",         "()",                            "number","Pi constant"),
                new StdMethod("e",          "()",                            "number","Euler's number"),
                new StdMethod("isNaN",      "(x: number)",                   "bool",  "Is NaN"),
                new StdMethod("isInfinite", "(x: number)",                   "bool",  "Is infinite"),
                new StdMethod("toInt",      "(x: number)",                   "int",   "Cast to int")
        ));

        LIBRARIES.put("io", List.of(
                new StdMethod("readFile",    "(path: string)",               "string","Read file contents"),
                new StdMethod("writeFile",   "(path: string, content: string)", "void","Write to file"),
                new StdMethod("appendFile",  "(path: string, content: string)", "void","Append to file"),
                new StdMethod("readLines",   "(path: string)",               "list",  "Read lines as list"),
                new StdMethod("readBytes",   "(path: string)",               "bytes", "Read raw bytes"),
                new StdMethod("deleteFile",  "(path: string)",               "void",  "Delete file"),
                new StdMethod("fileExists",  "(path: string)",               "bool",  "Check if file exists"),
                new StdMethod("isDir",       "(path: string)",               "bool",  "Check if path is dir"),
                new StdMethod("listDir",     "(path: string)",               "list",  "List directory contents"),
                new StdMethod("makeDir",     "(path: string)",               "void",  "Create directory"),
                new StdMethod("copyFile",    "(src: string, dest: string)",  "void",  "Copy file"),
                new StdMethod("moveFile",    "(src: string, dest: string)",  "void",  "Move file"),
                new StdMethod("fileSize",    "(path: string)",               "long",  "File size in bytes"),
                new StdMethod("cwd",         "()",                           "string","Current working directory"),
                new StdMethod("absPath",     "(path: string)",               "string","Absolute path"),
                new StdMethod("openWriter",  "(path: string)",               "writer","Open file writer"),
                new StdMethod("writeLine",   "(writer: any, line: string)",  "void",  "Write line"),
                new StdMethod("closeWriter", "(writer: any)",                "void",  "Close writer"),
                new StdMethod("openReader",  "(path: string)",               "reader","Open file reader"),
                new StdMethod("readLine",    "(reader: any)",                "string","Read line"),
                new StdMethod("closeReader", "(reader: any)",                "void",  "Close reader")
        ));

        LIBRARIES.put("string", List.of(
                new StdMethod("strLen",        "(s: string)",                       "int",    "String length"),
                new StdMethod("strUpper",      "(s: string)",                       "string", "Uppercase"),
                new StdMethod("strLower",      "(s: string)",                       "string", "Lowercase"),
                new StdMethod("strTrim",       "(s: string)",                       "string", "Trim whitespace"),
                new StdMethod("strTrimLeft",   "(s: string)",                       "string", "Trim left"),
                new StdMethod("strTrimRight",  "(s: string)",                       "string", "Trim right"),
                new StdMethod("strContains",   "(s: string, sub: string)",          "bool",   "Contains substring"),
                new StdMethod("strStartsWith", "(s: string, prefix: string)",       "bool",   "Starts with"),
                new StdMethod("strEndsWith",   "(s: string, suffix: string)",       "bool",   "Ends with"),
                new StdMethod("strIndexOf",    "(s: string, sub: string)",          "int",    "Index of substring"),
                new StdMethod("strSlice",      "(s: string, start: int, end: int)", "string", "Slice string"),
                new StdMethod("strReplace",    "(s: string, target: string, replacement: string)", "string","Replace"),
                new StdMethod("strReplaceAll", "(s: string, target: string, replacement: string)", "string","Replace all"),
                new StdMethod("strSplit",      "(s: string, delimiter: string)",    "list",   "Split string"),
                new StdMethod("strJoin",       "(list: list, delimiter: string)",   "string", "Join list"),
                new StdMethod("strRepeat",     "(s: string, times: int)",           "string", "Repeat string"),
                new StdMethod("strReverse",    "(s: string)",                       "string", "Reverse string"),
                new StdMethod("strCharAt",     "(s: string, index: int)",           "string", "Char at index"),
                new StdMethod("strToNumber",   "(s: string)",                       "number", "Parse number"),
                new StdMethod("strFromNumber", "(n: number)",                       "string", "Number to string"),
                new StdMethod("strIsEmpty",    "(s: string)",                       "bool",   "Is empty"),
                new StdMethod("strPadLeft",    "(s: string, length: int, padChar: string)", "string","Pad left"),
                new StdMethod("strPadRight",   "(s: string, length: int, padChar: string)", "string","Pad right"),
                new StdMethod("strChars",      "(s: string)",                       "list",   "Characters as list"),
                new StdMethod("strFormat",     "(template: string, args: any)",     "string", "Format string")
        ));

        LIBRARIES.put("list", List.of(
                new StdMethod("listNew",       "()",                           "list",  "Create empty list"),
                new StdMethod("listAdd",       "(list: list, item: any)",      "void",  "Add item"),
                new StdMethod("listAddAt",     "(list: list, index: int, item: any)", "void","Insert at index"),
                new StdMethod("listRemove",    "(list: list, index: int)",     "any",   "Remove at index"),
                new StdMethod("listGet",       "(list: list, index: int)",     "any",   "Get at index"),
                new StdMethod("listSet",       "(list: list, index: int, value: any)", "void","Set at index"),
                new StdMethod("listSize",      "(list: list)",                 "int",   "List size"),
                new StdMethod("listContains",  "(list: list, item: any)",      "bool",  "Contains item"),
                new StdMethod("listIndexOf",   "(list: list, item: any)",      "int",   "Index of item"),
                new StdMethod("listSlice",     "(list: list, start: int, end: int)", "list","Slice"),
                new StdMethod("listReverse",   "(list: list)",                 "void",  "Reverse in place"),
                new StdMethod("listSort",      "(list: list)",                 "void",  "Sort in place"),
                new StdMethod("listConcat",    "(list1: list, list2: list)",   "list",  "Concatenate lists"),
                new StdMethod("listFlat",      "(list: list)",                 "list",  "Flatten nested list"),
                new StdMethod("listUnique",    "(list: list)",                 "list",  "Remove duplicates"),
                new StdMethod("listFill",      "(value: any, times: int)",     "list",  "Fill list"),
                new StdMethod("listClear",     "(list: list)",                 "void",  "Clear list"),
                new StdMethod("listIsEmpty",   "(list: list)",                 "bool",  "Is empty"),
                new StdMethod("listFirst",     "(list: list)",                 "any",   "First element"),
                new StdMethod("listLast",      "(list: list)",                 "any",   "Last element"),
                new StdMethod("listPop",       "(list: list)",                 "any",   "Pop last element"),
                new StdMethod("listShuffle",   "(list: list)",                 "void",  "Shuffle in place")
        ));

        LIBRARIES.put("map", List.of(
                new StdMethod("mapNew",             "()",                       "map",   "Create empty map"),
                new StdMethod("mapSet",             "(map: map, key: any, value: any)", "void","Set key-value"),
                new StdMethod("mapGet",             "(map: map, key: any)",     "any",   "Get by key"),
                new StdMethod("mapHas",             "(map: map, key: any)",     "bool",  "Has key"),
                new StdMethod("mapRemove",          "(map: map, key: any)",     "void",  "Remove key"),
                new StdMethod("mapSize",            "(map: map)",               "int",   "Map size"),
                new StdMethod("mapKeys",            "(map: map)",               "list",  "All keys"),
                new StdMethod("mapValues",          "(map: map)",               "list",  "All values"),
                new StdMethod("mapClear",           "(map: map)",               "void",  "Clear map"),
                new StdMethod("mapIsEmpty",         "(map: map)",               "bool",  "Is empty"),
                new StdMethod("mapMerge",           "(map1: map, map2: map)",   "map",   "Merge maps"),
                new StdMethod("mapContainsValue",   "(map: map, value: any)",   "bool",  "Contains value"),
                new StdMethod("mapToList",          "(map: map)",               "list",  "Convert to list")
        ));

        LIBRARIES.put("http", List.of(
                new StdMethod("httpGet",       "(url: string)",                          "response","HTTP GET"),
                new StdMethod("httpPost",      "(url: string, body: string)",            "response","HTTP POST"),
                new StdMethod("httpPostJson",  "(url: string, json: string)",            "response","HTTP POST JSON"),
                new StdMethod("httpPut",       "(url: string, body: string)",            "response","HTTP PUT"),
                new StdMethod("httpDelete",    "(url: string)",                          "response","HTTP DELETE"),
                new StdMethod("httpGetStatus", "(response: any)",                        "int",     "Get status code"),
                new StdMethod("httpGetBody",   "(response: any)",                        "string",  "Get body")
        ));

        LIBRARIES.put("system", List.of(
                new StdMethod("exit",          "(code: int)",                  "void",  "Exit program"),
                new StdMethod("getArgs",       "()",                           "list",  "Get command line args"),
                new StdMethod("getEnv",        "(key: string)",                "string","Get env variable"),
                new StdMethod("clock",         "()",                           "long",  "Current timestamp"),
                new StdMethod("nanoTime",      "()",                           "long",  "Current nanoseconds"),
                new StdMethod("sleep",         "(ms: int)",                    "void",  "Sleep milliseconds"),
                new StdMethod("exec",          "(command: string)",            "string","Execute command"),
                new StdMethod("osName",        "()",                           "string","OS name"),
                new StdMethod("osArch",        "()",                           "string","OS architecture"),
                new StdMethod("freeMemory",    "()",                           "long",  "Free memory"),
                new StdMethod("totalMemory",   "()",                           "long",  "Total memory"),
                new StdMethod("gc",            "()",                           "void",  "Force garbage collect")
        ));

        LIBRARIES.put("date", List.of(
                new StdMethod("now",         "()",                            "string","Current datetime"),
                new StdMethod("today",       "()",                            "string","Current date"),
                new StdMethod("year",        "(date: string)",                "int",   "Year from date"),
                new StdMethod("month",       "(date: string)",                "int",   "Month from date"),
                new StdMethod("day",         "(date: string)",                "int",   "Day from date"),
                new StdMethod("hour",        "(date: string)",                "int",   "Hour from date"),
                new StdMethod("minute",      "(date: string)",                "int",   "Minute from date"),
                new StdMethod("second",      "(date: string)",                "int",   "Second from date"),
                new StdMethod("formatDate",  "(date: string, pattern: string)","string","Format date"),
                new StdMethod("parseDate",   "(s: string, pattern: string)",  "string","Parse date"),
                new StdMethod("dateDiff",    "(date1: string, date2: string)","int",   "Diff in days"),
                new StdMethod("addDays",     "(date: string, n: int)",        "string","Add days"),
                new StdMethod("addMonths",   "(date: string, n: int)",        "string","Add months"),
                new StdMethod("dayOfWeek",   "(date: string)",                "int",   "Day of week"),
                new StdMethod("isLeapYear",  "(year: int)",                   "bool",  "Is leap year")
        ));

        LIBRARIES.put("base64", List.of(
                new StdMethod("base64Encode",  "(s: string)",                  "string","Base64 encode"),
                new StdMethod("base64Decode",  "(s: string)",                  "string","Base64 decode"),
                new StdMethod("urlEncode",     "(s: string)",                  "string","URL encode"),
                new StdMethod("urlDecode",     "(s: string)",                  "string","URL decode"),
                new StdMethod("hexEncode",     "(s: string)",                  "string","Hex encode"),
                new StdMethod("hexDecode",     "(s: string)",                  "string","Hex decode")
        ));

        LIBRARIES.put("regex", List.of(
                new StdMethod("compile",      "(pattern: string)",             "regex", "Compile regex"),
                new StdMethod("reset",        "(regex: regex, input: string)","void",  "Reset regex"),
                new StdMethod("matches",      "(regex: regex)",                "bool",  "Full match"),
                new StdMethod("find",         "(regex: regex)",                "bool",  "Find next match"),
                new StdMethod("group",        "(regex: regex, index: int)",   "string","Get capture group"),
                new StdMethod("groupCount",   "(regex: regex)",                "int",   "Number of groups"),
                new StdMethod("start",        "(regex: regex)",                "int",   "Match start"),
                new StdMethod("end",          "(regex: regex)",                "int",   "Match end"),
                new StdMethod("replaceFirst", "(regex: regex, replacement: string)", "string","Replace first"),
                new StdMethod("replaceAll",   "(regex: regex, replacement: string)", "string","Replace all"),
                new StdMethod("split",        "(regex: regex, input: string)","list",  "Split string"),
                new StdMethod("quote",        "(s: string)",                   "string","Quote literal")
        ));

        LIBRARIES.put("json", List.of(
                new StdMethod("jsonStringify", "(value: any)",                 "string","JSON stringify"),
                new StdMethod("jsonPretty",    "(value: any)",                 "string","Pretty print JSON"),
                new StdMethod("jsonParse",     "(s: string)",                  "any",   "Parse JSON"),
                new StdMethod("jsonGet",       "(json: any, key: string)",     "any",   "Get JSON field"),
                new StdMethod("jsonHas",       "(json: any, key: string)",     "bool",  "Has JSON field"),
                new StdMethod("jsonIsValid",   "(s: string)",                  "bool",  "Is valid JSON")
        ));

        LIBRARIES.put("jdbc", List.of(
                new StdMethod("dbConnect",       "(url: string, user: string, password: string)", "conn","Connect to DB"),
                new StdMethod("dbConnectPg",     "(host: string, port: int, db: string, user: string, password: string)", "conn","Connect PostgreSQL"),
                new StdMethod("dbConnectSql",    "(host: string, port: int, db: string, user: string, password: string)", "conn","Connect MySQL"),
                new StdMethod("dbConnectSqlite", "(path: string)",              "conn",  "Connect SQLite"),
                new StdMethod("dbClose",         "(conn: any)",                 "void",  "Close connection"),
                new StdMethod("dbQuery",         "(conn: any, sql: string)",    "result","Execute query"),
                new StdMethod("dbExecute",       "(conn: any, sql: string)",    "void",  "Execute statement"),
                new StdMethod("dbInsert",        "(conn: any, sql: string)",    "void",  "Insert row"),
                new StdMethod("dbUpdate",        "(conn: any, sql: string)",    "void",  "Update rows"),
                new StdMethod("dbDelete",        "(conn: any, sql: string)",    "void",  "Delete rows"),
                new StdMethod("dbBegin",         "(conn: any)",                 "void",  "Begin transaction"),
                new StdMethod("dbCommit",        "(conn: any)",                 "void",  "Commit transaction"),
                new StdMethod("dbRollback",      "(conn: any)",                 "void",  "Rollback transaction"),
                new StdMethod("dbPrepare",       "(conn: any, sql: string)",    "stmt",  "Prepare statement"),
                new StdMethod("dbTables",        "(conn: any)",                 "list",  "List tables")
        ));

        LIBRARIES.put("thread", List.of(
                new StdMethod("spawn",          "(func: any)",                  "thread","Spawn thread"),
                new StdMethod("threadSleep",    "(ms: int)",                    "void",  "Sleep"),
                new StdMethod("threadId",       "()",                           "int",   "Current thread ID"),
                new StdMethod("threadName",     "()",                           "string","Current thread name"),
                new StdMethod("threadCount",    "()",                           "int",   "Thread count"),
                new StdMethod("cpuCount",       "()",                           "int",   "CPU count"),
                new StdMethod("threadIsAlive",  "(thread: any)",                "bool",  "Is thread alive"),
                new StdMethod("threadJoin",     "(thread: any)",                "void",  "Join thread"),
                new StdMethod("channelNew",     "()",                           "channel","Create channel"),
                new StdMethod("channelSend",    "(channel: any, value: any)",   "void",  "Send to channel"),
                new StdMethod("channelRecv",    "(channel: any)",               "any",   "Receive from channel"),
                new StdMethod("channelClose",   "(channel: any)",               "void",  "Close channel")
        ));

        LIBRARIES.put("socket", List.of(
                new StdMethod("connect",        "(host: string, port: int)",    "socket","Connect"),
                new StdMethod("importSocket",   "(socket: any)",                "socket","Import socket"),
                new StdMethod("send",           "(socket: any, message: string)","void", "Send message"),
                new StdMethod("receive",        "(socket: any)",                "string","Receive message"),
                new StdMethod("writeBytes",     "(socket: any, bytes: any)",    "void",  "Write bytes"),
                new StdMethod("readBytes",      "(socket: any, n: int)",        "bytes", "Read bytes"),
                new StdMethod("setSoTimeout",   "(socket: any, ms: int)",       "void",  "Set timeout"),
                new StdMethod("setTcpNoDelay",  "(socket: any, flag: bool)",    "void",  "Set TCP no-delay"),
                new StdMethod("setKeepAlive",   "(socket: any, flag: bool)",    "void",  "Set keep-alive"),
                new StdMethod("getRemoteAddr",  "(socket: any)",                "string","Remote address"),
                new StdMethod("close",          "(socket: any)",                "void",  "Close socket")
        ));

        LIBRARIES.put("server", List.of(
                new StdMethod("bind",           "(port: int)",                  "server","Bind server"),
                new StdMethod("accept",         "(server: any)",                "socket","Accept connection"),
                new StdMethod("setSoTimeout",   "(server: any, ms: int)",       "void",  "Set timeout"),
                new StdMethod("close",          "(server: any)",                "void",  "Close server"),
                new StdMethod("getPort",        "(server: any)",                "int",   "Get port"),
                new StdMethod("isClosed",       "(server: any)",                "bool",  "Is closed")
        ));

        LIBRARIES.put("properties", List.of(
                new StdMethod("loadProp",      "(path: string)",                "void",  "Load properties"),
                new StdMethod("getProp",       "(key: string)",                 "string","Get property"),
                new StdMethod("getPropOr",     "(key: string, default: string)","string","Get with default"),
                new StdMethod("setProp",       "(key: string, value: string)",  "void",  "Set property"),
                new StdMethod("hasProp",       "(key: string)",                 "bool",  "Has property"),
                new StdMethod("removeProp",    "(key: string)",                 "void",  "Remove property"),
                new StdMethod("getAllProps",   "()",                            "map",   "Get all properties"),
                new StdMethod("saveProps",     "(path: string)",                "void",  "Save properties"),
                new StdMethod("loadPropsMap",  "(path: string)",                "map",   "Load as map")
        ));

        LIBRARIES.put("converter", List.of(
                new StdMethod("toNumber",  "(x: any)",                          "number","Convert to number"),
                new StdMethod("toString",  "(x: any)",                          "string","Convert to string"),
                new StdMethod("toBool",    "(x: any)",                          "bool",  "Convert to bool"),
                new StdMethod("toInt",     "(x: any)",                          "int",   "Convert to int"),
                new StdMethod("toHex",     "(x: any)",                          "string","Convert to hex"),
                new StdMethod("toOctal",   "(x: any)",                          "string","Convert to octal"),
                new StdMethod("toBinary",  "(x: any)",                          "string","Convert to binary"),
                new StdMethod("fromHex",   "(s: string)",                       "int",   "Parse hex"),
                new StdMethod("fromOctal", "(s: string)",                       "int",   "Parse octal"),
                new StdMethod("fromBinary","(s: string)",                       "int",   "Parse binary"),
                new StdMethod("toBytes",   "(x: any)",                          "bytes", "Convert to bytes"),
                new StdMethod("fromBytes", "(bytes: any)",                      "any",   "Parse bytes"),
                new StdMethod("typeOf",    "(x: any)",                          "string","Get type name")
        ));
    }

    /** Returns all method names from a given library. */
    public static List<String> getMethodNames(String libName) {
        List<StdMethod> methods = LIBRARIES.get(libName);
        return methods != null ? methods.stream().map(StdMethod::name).toList() : List.of();
    }

    /** Checks if a function name exists in any library. */
    public static boolean isKnownStdFunction(String name) {
        for (List<StdMethod> methods : LIBRARIES.values()) {
            for (StdMethod m : methods) {
                if (m.name().equals(name)) return true;
            }
        }
        return false;
    }

    /** Returns the library name that contains the given method, or null. */
    public static String findLibraryForMethod(String methodName) {
        for (Map.Entry<String, List<StdMethod>> entry : LIBRARIES.entrySet()) {
            for (StdMethod m : entry.getValue()) {
                if (m.name().equals(methodName)) return entry.getKey();
            }
        }
        return null;
    }

    /** Returns the StdMethod for a given name, or null. */
    @Nullable
    public static StdMethod findMethod(String methodName) {
        for (List<StdMethod> methods : LIBRARIES.values()) {
            for (StdMethod m : methods) {
                if (m.name().equals(methodName)) return m;
            }
        }
        return null;
    }

    /** Returns all method names across all libraries (for searching). */
    public static Set<String> getAllMethodNames() {
        Set<String> names = new LinkedHashSet<>();
        for (List<StdMethod> methods : LIBRARIES.values()) {
            for (StdMethod m : methods) names.add(m.name());
        }
        return names;
    }

    /** Returns all method names for a given library. */
    public static List<String> getMethodNamesForLib(String libName) {
        List<StdMethod> methods = LIBRARIES.get(libName);
        return methods != null ? methods.stream().map(StdMethod::name).toList() : List.of();
    }
}
