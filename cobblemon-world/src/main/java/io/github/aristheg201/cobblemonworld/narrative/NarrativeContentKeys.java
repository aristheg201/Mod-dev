package io.github.aristheg201.cobblemonworld.narrative;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
/** Language existence checks are content validation, never runtime gameplay proof. */
public final class NarrativeContentKeys {
    private static Set<String> keys;
    public static boolean has(String key){if(keys==null)try(var in=NarrativeContentKeys.class.getClassLoader().getResourceAsStream("assets/cobblemonworld/lang/en_us.json")){keys=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().keySet();}catch(Exception e){throw new IllegalStateException("Cannot load narrative localization index",e);}return keys.contains(key);}
}
