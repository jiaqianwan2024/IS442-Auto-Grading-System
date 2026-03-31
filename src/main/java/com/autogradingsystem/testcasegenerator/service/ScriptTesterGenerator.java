package com.autogradingsystem.testcasegenerator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ScriptTesterGenerator {

    public String generate(String questionId, int maxScore, String description) {
        return generate(questionId, maxScore, description, null);
    }

    public String generate(String questionId, int maxScore, String description, Path templateZip) {
        double safeScore = Math.max(1, maxScore);
        boolean oneLiner = expectsOneLiner(description);
        boolean forbidBoth = forbidsBothScripts(description);
        boolean forbidResource = forbidsResourceReference(description);
        boolean exactOut = requiresOutDirectory(description);
        boolean strictClasspath = forbidsUnnecessaryClasspathEntries(description);
        String expectedMain = extractExpectedMainClass(description);
        String expectedOut = extractExpectedOutputDir(description);
        String expectedStdout = extractExpectedStdout(description);
        java.util.List<String> requiredClasspathEntries =
                extractRequiredClasspathEntries(description, exactOut ? expectedOut : null);
        String comment = cleanComment(description);
        Map<String, String> baselines = loadTemplateScriptBaselines(templateZip);
        boolean keepCompileScripts = shouldKeepCompileScriptsUnchanged(description, baselines);
        boolean keepRunScripts = shouldKeepRunScriptsUnchanged(description, baselines);

        String scoreBlock = buildScoreBlock(keepCompileScripts, keepRunScripts, expectedStdout != null);
        int requiredChecks = countChecks(scoreBlock);

        String tpl = """
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** AUTO-GENERATED SCRIPT TESTER - __Q__
__C__ */
public class __T__ {
    static double score;
    static int passedChecks;
    static final double FULL_SCORE=(__FULL_SCORE__);
    static final int REQUIRED_CHECKS=(__REQUIRED_CHECKS__);
    static final boolean ONE=__ONE__;
    static final boolean FORBID_BOTH=__FORBID_BOTH__;
    static final boolean FORBID_RESOURCE=__FORBID_RESOURCE__;
    static final boolean STRICT_CP=__STRICT_CP__;
    static final boolean KEEP_COMPILE_SCRIPTS=__KEEP_COMPILE_SCRIPTS__;
    static final boolean KEEP_RUN_SCRIPTS=__KEEP_RUN_SCRIPTS__;
    static final String EXPECTED_OUT=__EXPECTED_OUT__;
    static final String EXPECTED_MAIN=__EXPECTED_MAIN__;
    static final String EXPECTED_STDOUT=__EXPECTED_STDOUT__;
    static final String[] REQUIRED_CP_ENTRIES=__REQUIRED_CP_ENTRIES__;
    static final String BASELINE_COMPILE_BAT=__BASELINE_COMPILE_BAT__;
    static final String BASELINE_COMPILE_SH=__BASELINE_COMPILE_SH__;
    static final String BASELINE_RUN_BAT=__BASELINE_RUN_BAT__;
    static final String BASELINE_RUN_SH=__BASELINE_RUN_SH__;

    public static void main(String[] a){
        File root=a!=null&&a.length>0&&!a[0].isBlank()?new File(a[0]):new File(".");
        try{grade(root.getCanonicalFile());}
        catch(Exception e){System.out.println("Fatal tester error: "+e.getMessage());}
        finally{
            score = passedChecks == REQUIRED_CHECKS ? FULL_SCORE : 0.0;
            System.out.printf("FINAL_SCORE:%.2f%n",score);
            System.exit(0);
        }
    }

    static void grade(File r){
        File compileBat=find(r,"compile.bat"), compileSh=find(r,"compile.sh");
        File runBat=find(r,"run.bat"), runSh=find(r,"run.sh");
        File c=pick(compileBat,compileSh), u=pick(runBat,runSh);

        Cmd cc=cmd(c,true), rc=cmd(u,false);

        String out=cc==null?null:outDir(cc.t);
        String expectOut=expectedOut(r);
        String expect=EXPECTED_MAIN!=null?EXPECTED_MAIN:detectMain(r);
        String actual=rc==null?null:mainTok(rc.t);
        List<String> cp=rc==null?List.of():cp(rc.t);
        List<String> jars=jars(r);
        List<String> requiredCp=requiredClasspathEntries(out,jars);

        Res cr=cc==null?Res.fail("no compile"):exec(r,c,cc);
        Res rr=rc==null?Res.fail("no run"):exec(r,u,rc);

__SCORE_BLOCK__
    }

    static void chk(String n, boolean ok, String a){
        System.out.println("Test: "+n);
        System.out.println("Actual    :|"+a+"|");
        if(ok){passedChecks++;System.out.println("Passed");}
        else System.out.println("Failed");
        System.out.println("-------------------------------------------------------");
    }

    static Res exec(File r, File s, Cmd c){
        if(s!=null){
            Res n=nativeExec(r,s);
            if(n.started) return n;
        }
        return run(r,adj(c.t),"direct");
    }

    static Res nativeExec(File r, File s){
        String n=s.getName().toLowerCase(Locale.ROOT);
        if(n.endsWith(".bat")){
            if(!win()) return Res.skip("bat on non-Windows");
            return run(r,List.of("cmd","/c",s.getAbsolutePath()),"native");
        }
        if(!hasCmd("sh")) return Res.skip("sh missing");
        return run(r,List.of("sh",s.getAbsolutePath()),"native");
    }

    static List<String> adj(List<String> t){
        List<String> a=new ArrayList<>(t);
        for(int i=0;i<a.size()-1;i++) if("-cp".equals(a.get(i))||"-classpath".equals(a.get(i))) a.set(i+1,normCp(a.get(i+1)));
        return a;
    }

    static Res run(File r,List<String> c,String m){
        ExecutorService ex=Executors.newSingleThreadExecutor();
        try{
            ProcessBuilder pb=new ProcessBuilder(c);pb.directory(r);pb.redirectErrorStream(true);
            Process p=pb.start();
            Future<String> o=ex.submit(()->read(p));
            if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();return Res.fail(m+" timeout");}
            String out=""; try{out=o.get(2,TimeUnit.SECONDS);}catch(Exception ignore){}
            return p.exitValue()==0?Res.pass(m+" exit=0",out):Res.fail(m+" exit="+p.exitValue()+trim(out));
        }catch(Exception e){return Res.fail(m+" failed: "+e.getMessage());}
        finally{ex.shutdownNow();}
    }

    static String read(Process p) throws IOException{
        try(BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
            StringBuilder sb=new StringBuilder(); String l; int c=0;
            while((l=br.readLine())!=null&&c<80){ if(c++>0) sb.append("\\n"); sb.append(l.stripTrailing());}
            return sb.toString();
        }
    }

    static String trim(String s){
        if(s==null||s.isBlank()) return "";
        String flat=s.replace("\\r","").replace("\\n"," | ");
        return " | "+(flat.length()>180?flat.substring(0,180)+"...":flat);
    }
    static int logical(File f){return lines(f).size();}
    static boolean exists(File f){return f!=null&&f.isFile();}
    static File pick(File a, File b){return exists(a)?a:b;}
    static boolean exactlyOne(File a, File b){return exists(a)^exists(b);}
    static String normScript(String s){
        if(s==null) return "";
        return s.replace("\\r\\n","\\n")
                .replace("\\r","\\n")
                .replaceAll("(?m)[ \\t]+$","")
                .trim();
    }
    static boolean scriptMatches(File f, String baseline){
        if(baseline==null) return true;
        if(f==null||!f.isFile()) return false;
        try { return normScript(Files.readString(f.toPath(), StandardCharsets.UTF_8)).equals(baseline); }
        catch (Exception e) { return false; }
    }
    static boolean compileScriptsMatchOriginal(File compileBat, File compileSh){
        return scriptMatches(compileBat, BASELINE_COMPILE_BAT) && scriptMatches(compileSh, BASELINE_COMPILE_SH);
    }
    static boolean runScriptsMatchOriginal(File runBat, File runSh){
        return scriptMatches(runBat, BASELINE_RUN_BAT) && scriptMatches(runSh, BASELINE_RUN_SH);
    }
    static String baselineSummary(File file, String baseline, String label){
        if(baseline==null) return label + "=n/a";
        return label + "=" + (scriptMatches(file, baseline) ? "unchanged" : "modified");
    }

    static List<String> lines(File f){
        if(f==null||!f.isFile()) return List.of();
        try{
            List<String> raw=Files.readAllLines(f.toPath(),StandardCharsets.UTF_8), out=new ArrayList<>();
            StringBuilder cur=new StringBuilder(); boolean bat=f.getName().toLowerCase(Locale.ROOT).endsWith(".bat");
            for(String r:raw){
                String l=strip(r,bat); if(l.isBlank()){ if(cur.length()>0){out.add(cur.toString());cur.setLength(0);} continue; }
                boolean cont=bat?l.endsWith("^"):l.endsWith("\\\\");
                if(cont) l=l.substring(0,l.length()-1).trim();
                if(cur.length()>0) cur.append(' '); cur.append(l);
                if(!cont){out.add(cur.toString());cur.setLength(0);}
            }
            if(cur.length()>0) out.add(cur.toString());
            return out;
        }catch(Exception e){return List.of();}
    }

    static String strip(String l, boolean bat){
        String t=l==null?"":l.trim();
        if(bat&&(t.startsWith("REM ")||t.equalsIgnoreCase("REM")||t.startsWith("::"))) return "";
        if(!bat&&(t.startsWith("#!")||t.startsWith("#"))) return "";
        if(bat&&(t.equalsIgnoreCase("@echo off")||t.equalsIgnoreCase("echo off")
                || t.equalsIgnoreCase("setlocal") || t.equalsIgnoreCase("endlocal"))) return "";
        return t;
    }

    static Cmd cmd(File f, boolean compile){
        for(String l:lines(f)){
            String low=l.toLowerCase(Locale.ROOT);
            if(compile && !low.contains("javac")) continue;
            if(!compile && !(low.contains("java ")||low.endsWith("java")||low.contains("java.exe"))) continue;
            List<String> t=tok(l); if(t.isEmpty()) continue;
            String first=new File(t.get(0)).getName().toLowerCase(Locale.ROOT);
            if(compile && !first.contains("javac")) continue;
            if(!compile && !(first.equals("java")||first.equals("java.exe"))) continue;
            return new Cmd(t,l);
        }
        return null;
    }

    static List<String> tok(String c){
        List<String> t=new ArrayList<>(); StringBuilder cur=new StringBuilder(); boolean q=false;
        for(int i=0;i<c.length();i++){
            char ch=c.charAt(i);
            if(ch=='"'){q=!q;continue;}
            if(Character.isWhitespace(ch)&&!q){if(cur.length()>0){t.add(cur.toString());cur.setLength(0);}}
            else cur.append(ch);
        }
        if(cur.length()>0) t.add(cur.toString());
        return t;
    }

    static String outDir(List<String> t){for(int i=0;i<t.size()-1;i++) if("-d".equals(t.get(i))) return t.get(i+1); return null;}

    static List<String> cp(List<String> t){
        for(int i=0;i<t.size()-1;i++){
            if("-cp".equals(t.get(i))||"-classpath".equals(t.get(i))){
                String[] p=normCp(t.get(i+1)).split(Pattern.quote(File.pathSeparator));
                List<String> o=new ArrayList<>(); for(String s:p) if(!s.isBlank()) o.add(s.trim()); return o;
            }
        }
        return List.of();
    }

    static String mainTok(List<String> t){
        for(int i=1;i<t.size();i++){
            String s=t.get(i);
            if("-cp".equals(s)||"-classpath".equals(s)){i++;continue;}
            if(s.startsWith("-")) continue;
            return s;
        }
        return null;
    }

    static String detectMain(File r){
        List<File> fs=new ArrayList<>(); javaFiles(r,fs);
        for(File f:fs) try{
            if(f.getName().endsWith("Tester.java")) continue;
            String s=Files.readString(f.toPath());
            if(!s.contains("public static void main(")) continue;
            Matcher pm=Pattern.compile("(?m)^\\\\s*package\\\\s+([\\\\w.]+)\\\\s*;").matcher(s);
            Matcher cm=Pattern.compile("(?m)^\\\\s*public\\\\s+class\\\\s+(\\\\w+)").matcher(s);
            String p=pm.find()?pm.group(1).trim():"";
            if(cm.find()) return p.isBlank()?cm.group(1).trim():p+"."+cm.group(1).trim();
        }catch(Exception ignore){}
        return null;
    }

    static void javaFiles(File r,List<File> o){
        if(r==null||!r.exists()) return; File[] fs=r.listFiles(); if(fs==null) return;
        for(File f:fs) if(f.isDirectory()) javaFiles(f,o); else if(f.getName().endsWith(".java")&&!f.getName().endsWith("Tester.java")) o.add(f);
    }

    static boolean classExists(File r,String out,String main){
        return new File(r,out+File.separator+main.replace('.',File.separatorChar)+".class").isFile();
    }

    static List<String> jars(File r){
        File e=new File(r,"external"); if(!e.isDirectory()) return List.of();
        List<String> o=new ArrayList<>(); Deque<File> st=new ArrayDeque<>(); st.push(e);
        while(!st.isEmpty()){
            File c=st.pop(); File[] fs=c.listFiles(); if(fs==null) continue;
            for(File f:fs) if(f.isDirectory()) st.push(f); else if(f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) o.add(f.getName());
        }
        Collections.sort(o); return o;
    }

    static boolean has(List<String> cp,String e){
        String n=npath(e), b=new File(e).getName().toLowerCase(Locale.ROOT);
        for(String s:cp) if(npath(s).equals(n)||new File(s).getName().toLowerCase(Locale.ROOT).equals(b)) return true;
        return false;
    }

    static boolean hasAll(List<String> cp,List<String> jars){ for(String j:jars) if(!has(cp,j)) return false; return true; }
    static boolean mentionsResource(String s){ return s!=null&&s.toLowerCase(Locale.ROOT).contains("resource"); }

    static boolean classpathLooksTight(List<String> cp,List<String> required){
        if(cp.isEmpty()) return false;
        Set<String> allowed=new LinkedHashSet<>();
        for(String entry:required){
            allowed.add(npath(entry));
            allowed.add(new File(entry).getName().toLowerCase(Locale.ROOT));
        }
        for(String entry:cp){
            String path=npath(entry);
            String name=new File(entry).getName().toLowerCase(Locale.ROOT);
            if(path.contains("resource")||path.contains("resources")||path.contains("src")||path.contains("source")) return false;
            if(allowed.contains(path)||allowed.contains(name)) continue;
            if(name.endsWith(".jar")) continue;
            if(path.equals(".")||path.equals("")) continue;
            return false;
        }
        return true;
    }

    static List<String> requiredClasspathEntries(String out,List<String> jars){
        List<String> req=new ArrayList<>();
        if(REQUIRED_CP_ENTRIES!=null) for(String entry:REQUIRED_CP_ENTRIES) if(entry!=null&&!entry.isBlank()) req.add(entry);
        if(req.isEmpty()){
            if(out!=null&&!out.isBlank()) req.add(out);
            req.addAll(jars);
        }
        return req;
    }

    static String expectedOut(File r){
        if(EXPECTED_OUT!=null) return EXPECTED_OUT;
        if(new File(r,"out").isDirectory()) return "out";
        if(new File(r,"generated").isDirectory()) return "generated";
        if(new File(r,"classes").isDirectory()) return "classes";
        return null;
    }

    static boolean mainMatches(String expected,String actual){
        if(expected==null) return true;
        if(actual==null||actual.isBlank()) return false;
        if(expected.equals(actual)) return true;
        int expectedDot=expected.lastIndexOf('.');
        int actualDot=actual.lastIndexOf('.');
        String expectedSimple=expectedDot>=0?expected.substring(expectedDot+1):expected;
        String actualSimple=actualDot>=0?actual.substring(actualDot+1):actual;
        return expectedSimple.equals(actualSimple);
    }

    static String normOutText(String s){
        if(s==null) return "";
        return s.replace("\\r\\n","\\n")
                .replace("\\r","\\n")
                .replaceAll("(?m)[ \\t]+$","")
                .trim();
    }

    static boolean stdoutMatches(String actual){
        return EXPECTED_STDOUT!=null && normOutText(EXPECTED_STDOUT).equals(normOutText(actual));
    }

    static String normCp(String s){ return s.replace(";",File.pathSeparator).replace(":",File.pathSeparator); }
    static String npath(String s){ return s.replace("\\\\","/").replace("./","").replace(".\\\\","").toLowerCase(Locale.ROOT); }

    static File find(File r,String...n){
        if(r==null||!r.exists()) return null; Set<String>w=new LinkedHashSet<>(); for(String s:n) w.add(s.toLowerCase(Locale.ROOT));
        Deque<File> st=new ArrayDeque<>(); st.push(r);
        while(!st.isEmpty()){
            File c=st.pop(); File[] fs=c.listFiles(); if(fs==null) continue; Arrays.sort(fs,Comparator.comparing(File::getName));
            for(File f:fs) if(f.isDirectory()) st.push(f); else if(w.contains(f.getName().toLowerCase(Locale.ROOT))) return f;
        }
        return null;
    }

    static boolean hasCmd(String c){
        try{
            ProcessBuilder pb=win()?new ProcessBuilder("where",c):new ProcessBuilder("sh","-c","command -v "+c);
            pb.redirectErrorStream(true); Process p=pb.start(); return p.waitFor(5,TimeUnit.SECONDS)&&p.exitValue()==0;
        }catch(Exception e){return false;}
    }

    static boolean win(){ return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"); }

    static class Cmd{ List<String> t; String raw; Cmd(List<String> t,String raw){this.t=t;this.raw=raw;} }
    static class Res{
        boolean started,ok; String m,o;
        Res(boolean s,boolean ok,String m,String o){started=s;this.ok=ok;this.m=m;this.o=o==null?"":o;}
        static Res pass(String m,String o){return new Res(true,true,m,o);}
        static Res fail(String m){return new Res(true,false,m,"");}
        static Res skip(String m){return new Res(false,false,m,"");}
        String msg(){return o.isBlank()?m:m+" | "+o;}
    }
}
""";

        return tpl.replace("__Q__", questionId)
                .replace("__T__", questionId + "Tester")
                .replace("__FULL_SCORE__", String.format("%.1f", safeScore))
                .replace("__REQUIRED_CHECKS__", Integer.toString(requiredChecks))
                .replace("__ONE__", Boolean.toString(oneLiner))
                .replace("__FORBID_BOTH__", Boolean.toString(forbidBoth))
                .replace("__FORBID_RESOURCE__", Boolean.toString(forbidResource))
                .replace("__STRICT_CP__", Boolean.toString(strictClasspath))
                .replace("__KEEP_COMPILE_SCRIPTS__", Boolean.toString(keepCompileScripts))
                .replace("__KEEP_RUN_SCRIPTS__", Boolean.toString(keepRunScripts))
                .replace("__EXPECTED_OUT__", javaStringLiteral(exactOut ? expectedOut : null))
                .replace("__EXPECTED_MAIN__", javaStringLiteral(expectedMain))
                .replace("__EXPECTED_STDOUT__", javaMultilineStringLiteral(expectedStdout))
                .replace("__REQUIRED_CP_ENTRIES__", javaArrayLiteral(requiredClasspathEntries))
                .replace("__BASELINE_COMPILE_BAT__", javaStringLiteral(baselines.get("compile.bat")))
                .replace("__BASELINE_COMPILE_SH__", javaStringLiteral(baselines.get("compile.sh")))
                .replace("__BASELINE_RUN_BAT__", javaStringLiteral(baselines.get("run.bat")))
                .replace("__BASELINE_RUN_SH__", javaStringLiteral(baselines.get("run.sh")))
                .replace("__SCORE_BLOCK__", scoreBlock)
                .replace("__C__", comment.isBlank() ? "" : " * " + comment);
    }

    private String buildScoreBlock(boolean keepCompileScripts, boolean keepRunScripts, boolean exactStdout) {
        java.util.List<String> checks = new java.util.ArrayList<>();
        if (keepCompileScripts) {
            checks.add("chk(\"compile scripts left unchanged\", compileScriptsMatchOriginal(compileBat, compileSh), baselineSummary(compileBat, BASELINE_COMPILE_BAT, \"compile.bat\")+\", \"+baselineSummary(compileSh, BASELINE_COMPILE_SH, \"compile.sh\"));");
        }
        if (keepRunScripts) {
            checks.add("chk(\"run scripts left unchanged\", runScriptsMatchOriginal(runBat, runSh), baselineSummary(runBat, BASELINE_RUN_BAT, \"run.bat\")+\", \"+baselineSummary(runSh, BASELINE_RUN_SH, \"run.sh\"));");
        }
        checks.add("chk(\"compile script ready\", c!=null&&(!FORBID_BOTH||exactlyOne(compileBat,compileSh))&&(!ONE||logical(c)==1), \"script=\"+(c==null?\"missing\":c.getName())+\", lines=\"+logical(c));");
        checks.add("chk(\"run script ready\", u!=null&&(!FORBID_BOTH||exactlyOne(runBat,runSh))&&(!ONE||logical(u)==1), \"script=\"+(u==null?\"missing\":u.getName())+\", lines=\"+logical(u));");
        checks.add("chk(\"compile command valid\", cc!=null&&(!FORBID_RESOURCE||!mentionsResource(cc.raw)), cc==null?\"no javac\":cc.raw);");
        checks.add("chk(\"run command valid\", rc!=null&&(!FORBID_RESOURCE||!mentionsResource(rc.raw)), rc==null?\"no java\":rc.raw);");
        checks.add("chk(\"compile output dir correct\", out!=null&&(expectOut==null||has(List.of(out),expectOut)), \"actual=\"+out+\", expected=\"+expectOut);");
        checks.add("chk(\"runtime classpath valid\", !requiredCp.isEmpty()&&hasAll(cp,requiredCp)&&(!STRICT_CP||classpathLooksTight(cp,requiredCp)), \"required=\"+requiredCp+\", cp=\"+cp);");
        checks.add("chk(\"main class target correct\", mainMatches(expect,actual), \"expected=\"+expect+\", actual=\"+actual);");
        checks.add("chk(\"compile succeeds\", cr.ok, cr.msg());");
        checks.add("chk(\"compiled main class exists\", expect==null||(cr.ok&&out!=null&&classExists(r,out,expect)), \"expect=\"+expect+\", out=\"+out);");
        checks.add("chk(\"run succeeds\", rr.ok, rr.msg());");
        if (exactStdout) {
            checks.add("chk(\"stdout matches expected\", rr.ok&&stdoutMatches(rr.o), rr.ok?rr.o:rr.msg());");
        }
        StringBuilder sb = new StringBuilder();
        for (String check : checks) {
            sb.append("        ").append(check).append("\n");
        }
        return sb.toString().trim();
    }

    private int countChecks(String scoreBlock) {
        if (scoreBlock == null || scoreBlock.isBlank()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = scoreBlock.indexOf("chk(", idx)) >= 0) {
            count++;
            idx += 4;
        }
        return count;
    }

    private boolean expectsOneLiner(String description) {
        String lower = normalise(description);
        return lower.contains("one-liner") || lower.contains("one liner") || lower.contains("one-liners");
    }

    private boolean forbidsBothScripts(String description) {
        String lower = normalise(description);
        boolean mentionsEither = lower.contains(" either ") || lower.contains(" one of ");
        boolean mentionsNotBoth = lower.contains("do not write in both") || lower.contains("do not write both")
                || lower.contains("not both") || lower.contains("not in both")
                || lower.contains("only one of");
        boolean mentionsCompilePair = (lower.contains("compile.sh") && lower.contains("compile.bat"))
                || lower.contains("compile.sh/compile.bat")
                || lower.contains("compile.bat/compile.sh")
                || lower.contains("compile.sh or compile.bat")
                || lower.contains("compile.bat or compile.sh")
                || lower.contains("compile script");
        boolean mentionsRunPair = (lower.contains("run.sh") && lower.contains("run.bat"))
                || lower.contains("run.sh/run.bat")
                || lower.contains("run.bat/run.sh")
                || lower.contains("run.sh or run.bat")
                || lower.contains("run.bat or run.sh")
                || lower.contains("run script");
        return (mentionsEither || mentionsNotBoth) && (mentionsCompilePair || mentionsRunPair);
    }

    private boolean forbidsResourceReference(String description) {
        String lower = normalise(description);
        return (lower.contains("do not reference the resource folder")
                || lower.contains("do not reference resource folder")
                || lower.contains("do not reference the source folder")
                || lower.contains("do not reference resource")
                || lower.contains("must not reference the resource folder")
                || lower.contains("must not reference resource")
                || lower.contains("without referencing the resource folder"))
                && (lower.contains("compile") || lower.contains("run") || lower.contains("classpath") || lower.contains("sourcepath"));
    }

    private boolean requiresOutDirectory(String description) {
        String lower = normalise(description);
        return lower.contains("out folder")
                || lower.contains("'out' folder")
                || lower.contains("\"out\" folder")
                || lower.contains("compiled to the out")
                || lower.contains("compile to folder out")
                || lower.contains("compile to the out")
                || lower.contains("compile to out")
                || lower.contains("compiled to out");
    }

    private boolean forbidsUnnecessaryClasspathEntries(String description) {
        String lower = normalise(description);
        return lower.contains("do not include unnecessary folders/jars")
                || lower.contains("do not include unnecessary folders")
                || lower.contains("do not include unnecessary jars")
                || lower.contains("do not include unnecessary folders/jars in the sourcepath/classpath")
                || lower.contains("do not include unnecessary folders/jars in sourcepath/classpath")
                || lower.contains("unnecessary folders/jars")
                || lower.contains("unnecessary folders")
                || lower.contains("unnecessary jars")
                || lower.contains("sourcepath/classpath");
    }

    private boolean shouldKeepCompileScriptsUnchanged(String description, Map<String, String> baselines) {
        boolean hasCompileBaseline = baselines.get("compile.bat") != null || baselines.get("compile.sh") != null;
        if (!hasCompileBaseline) return false;
        String lower = normalise(description);
        if (explicitlyForbidsEditing(lower, true)) return true;
        return impliesJavaOnlyEdits(lower) && !mentionsCompileScriptAuthoring(lower);
    }

    private boolean shouldKeepRunScriptsUnchanged(String description, Map<String, String> baselines) {
        boolean hasRunBaseline = baselines.get("run.bat") != null || baselines.get("run.sh") != null;
        if (!hasRunBaseline) return false;
        String lower = normalise(description);
        if (explicitlyForbidsEditing(lower, false)) return true;
        return impliesJavaOnlyEdits(lower) && !mentionsRunScriptAuthoring(lower);
    }

    private boolean explicitlyForbidsEditing(String lower, boolean compile) {
        if (lower.isBlank()) return false;
        boolean mentionsSpecificScript = compile
                ? (lower.contains("compile.bat") || lower.contains("compile.sh") || lower.contains("compile script")
                || lower.contains("compile/run scripts") || lower.contains("compile and run scripts"))
                : (lower.contains("run.bat") || lower.contains("run.sh") || lower.contains("run script")
                || lower.contains("compile/run scripts") || lower.contains("compile and run scripts"));
        boolean forbidsEditing = lower.contains("do not edit") || lower.contains("must not edit")
                || lower.contains("do not touch") || lower.contains("must not touch")
                || lower.contains("leave unchanged") || lower.contains("remain unchanged")
                || lower.contains("without editing") || lower.contains("do not modify")
                || lower.contains("must not modify") || lower.contains("unchanged");
        if (mentionsSpecificScript && forbidsEditing) return true;
        if ((lower.contains("batch/sh files") || lower.contains(".bat/.sh") || lower.contains("bat/sh files"))
                && forbidsEditing) return true;
        return false;
    }

    private boolean impliesJavaOnlyEdits(String lower) {
        boolean mentionsJavaOnly = lower.contains("make the relevant changes to the java source files")
                || lower.contains("make the relevant changes to java source files")
                || lower.contains("modify java source files")
                || lower.contains("edit the java source files")
                || lower.contains("only modify java source files")
                || lower.contains("only modify the java source files")
                || lower.contains("only change java source files")
                || lower.contains("only change the java source files")
                || lower.contains("application.java contains the main() method")
                || lower.contains("application.java contains the main()")
                || lower.contains("application.java has main()")
                || lower.contains("application.java has the main()")
                || lower.contains("application.java has main method")
                || lower.contains("contains the main() method to test");
        boolean mentionsSomeScriptAction = mentionsCompileScriptAuthoring(lower) || mentionsRunScriptAuthoring(lower);
        return mentionsJavaOnly && !mentionsSomeScriptAction;
    }

    private boolean mentionsCompileScriptAuthoring(String lower) {
        return mentionsScriptAuthoring(lower, "compile");
    }

    private boolean mentionsRunScriptAuthoring(String lower) {
        return mentionsScriptAuthoring(lower, "run");
    }

    private boolean mentionsScriptAuthoring(String lower, String kind) {
        boolean mentionsScript = lower.contains(kind + ".bat") || lower.contains(kind + ".sh")
                || lower.contains(kind + " script") || lower.contains("write a one-liner in either " + kind)
                || lower.contains("write " + kind) || lower.contains("create " + kind);
        boolean mentionsAction = lower.contains("write") || lower.contains("create") || lower.contains("complete")
                || lower.contains("update") || lower.contains("modify") || lower.contains("edit")
                || lower.contains("one-liner") || lower.contains("one liner");
        return mentionsScript && mentionsAction;
    }

    private String extractExpectedMainClass(String description) {
        String text = description == null ? "" : description;
        Matcher javaFile = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\.java\\s+(?:contains|has)\\s+(?:the\\s+)?main(?:\\(\\))?(?:\\s+method)?\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (javaFile.find()) return javaFile.group(1);
        Matcher mainInJava = Pattern.compile("\\bmain\\s+method\\s+in\\s+([A-Z][A-Za-z0-9_]*)\\.java\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (mainInJava.find()) return mainInJava.group(1);
        Matcher className = Pattern.compile("\\brun\\s+the\\s+main\\s+method\\s+in\\s+([A-Z][A-Za-z0-9_.]*)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (className.find()) {
            String candidate = className.group(1).replace(".java", "");
            return candidate;
        }
        return null;
    }

    private String extractExpectedOutputDir(String description) {
        String text = description == null ? "" : description;
        Matcher quoted = Pattern.compile("\\b(?:compile(?:d)?\\s+to|compile(?:d)?\\s+into)\\s+(?:the\\s+)?(?:folder\\s+)?['\"]?([A-Za-z][A-Za-z0-9_-]*)['\"]?\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (quoted.find()) return quoted.group(1);
        if (requiresOutDirectory(description)) return "out";
        return null;
    }

    private String cleanComment(String description) {
        if (description == null) return "";
        return description.replace("\r", " ").replace("\n", " ").replace("*/", "* /").trim();
    }

    private String extractExpectedStdout(String description) {
        if (description == null || description.isBlank()) return null;
        String normalized = description.replace("\r\n", "\n").replace('\r', '\n');
        Matcher fenced = Pattern.compile("(?is)expected\\s+(?:stdout|output)\\s*[:\\-]\\s*(.+)$").matcher(normalized);
        if (fenced.find()) {
            String value = normalizeExpectedStdout(fenced.group(1).trim());
            return value.isBlank() ? null : value;
        }
        Matcher inline = Pattern.compile("(?is)following\\s+will\\s+be\\s+displayed[^:]*:\\s*(.+)$").matcher(normalized);
        if (inline.find()) {
            String value = normalizeExpectedStdout(inline.group(1).trim());
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private java.util.List<String> extractRequiredClasspathEntries(String description, String expectedOut) {
        java.util.LinkedHashSet<String> entries = new java.util.LinkedHashSet<>();
        String text = description == null ? "" : description;
        String lower = normalise(description);
        if (expectedOut != null && !expectedOut.isBlank()) {
            entries.add(expectedOut);
        }
        if (mentionsPathToken(lower, "given")) {
            entries.add("given");
        }
        if (mentionsPathToken(lower, "external")) {
            entries.add("external");
        }
        Matcher jarMatcher = Pattern.compile("([A-Za-z0-9_./\\\\-]+\\.jar)", Pattern.CASE_INSENSITIVE).matcher(text);
        while (jarMatcher.find()) {
            entries.add(jarMatcher.group(1));
        }
        if (forbidsUnnecessaryClasspathEntries(description)) {
            if (expectedOut != null && !expectedOut.isBlank()) entries.add(expectedOut);
            if (mentionsPathToken(lower, "given")) entries.add("given");
            if (mentionsPathToken(lower, "external")) entries.add("external");
        }
        return new java.util.ArrayList<>(entries);
    }

    private boolean mentionsPathToken(String lower, String token) {
        return lower.contains(" " + token + " ")
                || lower.contains("/" + token)
                || lower.contains(token + "/")
                || lower.contains("\\" + token)
                || lower.contains(token + "\\")
                || lower.contains(token + " folder")
                || lower.contains(token + " directory");
    }

    private String normalizeExpectedStdout(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.contains("\n")) {
            return trimInstructionTail(normalized);
        }

        normalized = normalized.replaceAll("\\s+(?=\\d+\\s*-\\s*\\S)", "\n");
        normalized = normalized.replaceAll("\\s+(?=Number\\s+of\\s+\\w+\\s*:)", "\n");
        return trimInstructionTail(normalized);
    }

    private String trimInstructionTail(String value) {
        String[] lines = value.split("\\n");
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
            if (looksLikeInstructionLine(lower)) break;
            kept.add(trimmed);
        }
        return String.join("\n", kept).trim();
    }

    private boolean looksLikeInstructionLine(String lower) {
        return lower.startsWith("do not ")
                || lower.startsWith("only modify ")
                || lower.startsWith("compile to ")
                || lower.startsWith("run main ")
                || lower.startsWith("application.java ")
                || lower.startsWith("write a ")
                || lower.startsWith("expected stdout")
                || lower.startsWith("expected output");
    }

    private String normalise(String description) {
        return description == null ? "" : (" " + description.toLowerCase().replace('\n', ' ').replace('\r', ' ') + " ");
    }

    private String javaStringLiteral(String value) {
        if (value == null || value.isBlank()) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String javaMultilineStringLiteral(String value) {
        if (value == null || value.isBlank()) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                + "\"";
    }

    private String javaArrayLiteral(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return "new String[0]";
        StringBuilder sb = new StringBuilder("new String[]{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(javaStringLiteral(values.get(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, String> loadTemplateScriptBaselines(Path templateZip) {
        Map<String, String> baselines = new LinkedHashMap<>();
        baselines.put("compile.bat", null);
        baselines.put("compile.sh", null);
        baselines.put("run.bat", null);
        baselines.put("run.sh", null);
        if (templateZip == null) return baselines;

        try (ZipInputStream zis = new ZipInputStream(java.nio.file.Files.newInputStream(templateZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = Path.of(entry.getName()).getFileName().toString().toLowerCase();
                if (!baselines.containsKey(name) || baselines.get(name) != null) {
                    zis.closeEntry();
                    continue;
                }
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                baselines.put(name, normalizeScriptBaseline(content));
                zis.closeEntry();
            }
        } catch (IOException ignored) {
        }
        return baselines;
    }

    private String normalizeScriptBaseline(String content) {
        if (content == null || content.isBlank()) return null;
        return content.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("(?m)[ \t]+$", "")
                .trim();
    }
}
