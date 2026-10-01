package vn.svarcade.tcg.integration;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.lang.reflect.Method;

/** The installed PB4 API is optional; no optional type occurs in this adapter's signatures. */
public final class PlaceholderBridge {
    private static Method context,parse;
    private static boolean initialized;
    private static synchronized void initialize(){if(initialized)return;initialized=true;
        if(!CardWorldsIntegrations.capabilities().has("placeholder-api"))return;
        try{Class<?> c=Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
            context=c.getMethod("of",ServerCommandSource.class);
            parse=Class.forName("eu.pb4.placeholders.api.Placeholders").getMethod("parseText",Text.class,c);
        }catch(ReflectiveOperationException e){org.slf4j.LoggerFactory.getLogger("cardworlds-integrations").warn("Placeholder API adapter unavailable",e);}
    }
    public static String value(ServerCommandSource player,String key){initialize();if(parse==null)return "—";
        try{Text result=(Text)parse.invoke(null,Text.literal("%"+key+"%"),context.invoke(null,player));
            return PlaceholderValues.resolve(result.getString(),ignored->null);
        }catch(ReflectiveOperationException e){return "—";}
    }
    public static String resolve(ServerCommandSource player,String text){return PlaceholderValues.resolve(text,key->value(player,key));}
    private PlaceholderBridge(){}
}
