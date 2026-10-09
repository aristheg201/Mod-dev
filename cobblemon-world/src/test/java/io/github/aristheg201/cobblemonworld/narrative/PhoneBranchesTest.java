package io.github.aristheg201.cobblemonworld.narrative;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhoneBranchesTest {
    JsonObject resource(String path)throws IOException {
        try(var in=getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in,path);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void everyLegacyPhoneChoiceHasItsOwnLocalizedReply()throws Exception {
        var vi=resource("assets/cobblemonworld/lang/vi_vn.json");var en=resource("assets/cobblemonworld/lang/en_us.json");int branches=0;
        for(String contact:List.of("mysterious","mara_voss","dr_orin","rook","selene_kade","aurelia")) {
            var messages=resource("data/cobblemonworld/contacts/"+contact+".json").getAsJsonArray("messages");
            var nodes=new HashMap<String,JsonObject>();for(var value:messages) {var node=value.getAsJsonObject();assertNull(nodes.put(node.get("id").getAsString(),node));}
            for(var node:nodes.values()) {
                var choices=node.getAsJsonArray("choices");if(choices==null || choices.isEmpty())continue;
                var destinations=new HashSet<String>();
                for(var value:choices) {
                    var choice=value.getAsJsonObject();assertTrue(destinations.add(choice.get("nextNode").getAsString()));
                    var reply=nodes.get(choice.get("nextNode").getAsString());assertNotNull(reply);
                    for(var locale:List.of(vi,en)) {
                        for(String key:List.of(node.get("text").getAsString(),choice.get("text").getAsString(),reply.get("text").getAsString())) {
                            assertTrue(locale.has(key),key);assertFalse(locale.get(key).getAsString().isBlank(),key);
                        }
                    }
                    branches++;
                }
            }
        }
        assertEquals(51,branches);
    }
}
