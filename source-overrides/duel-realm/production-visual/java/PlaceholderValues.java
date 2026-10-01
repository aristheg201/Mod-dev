package vn.svarcade.tcg.integration;

import java.util.*;
import java.util.function.Function;
import java.util.regex.*;

/** Plain-text results are substituted as data, never as MiniMessage markup. */
public final class PlaceholderValues {
    public static final Pattern TOKEN=Pattern.compile("%([^%\\s]+)%");
    public static String resolve(String input,Function<String,String> value){
        Matcher matcher=TOKEN.matcher(input);StringBuffer out=new StringBuffer();
        while(matcher.find()){String result=value.apply(matcher.group(1));
            matcher.appendReplacement(out,Matcher.quoteReplacement(result==null||result.isBlank()||TOKEN.matcher(result).find()?"—":result));}
        return matcher.appendTail(out).toString();
    }
    public static String escapeMini(String value){return value.replace("\\","\\\\").replace("<","\\<");}
    private PlaceholderValues(){}
}
