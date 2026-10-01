package vn.svarcade.tcg.integration;

import net.kyori.adventure.text.Component;
import java.util.function.Function;

/** Authored formatting is already parsed; provider results are always literal component data. */
public final class PlaceholderComponents {
    public static Component resolve(Component authored,Function<String,String> resolver){
        return authored.replaceText(config->config.match(PlaceholderValues.TOKEN).replacement((match,builder)->
            Component.text(PlaceholderValues.resolve("%"+match.group(1)+"%",resolver))));
    }
    private PlaceholderComponents(){}
}
