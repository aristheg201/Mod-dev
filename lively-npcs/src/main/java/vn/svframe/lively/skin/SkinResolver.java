package vn.svframe.lively.skin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.npc.MojangProfileResolver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Secure async resolver for Mojang names, signed textures, MineSkin IDs and allowlisted skin-page/PNG URLs. */
public final class SkinResolver implements AutoCloseable {
    private static final Pattern TEXTURE_URL = Pattern.compile("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PNG_URL = Pattern.compile("https?://[^\\\"'<>\\s]+?\\.png(?:\\?[^\\\"'<>\\s]*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE = Pattern.compile("<meta[^>]+(?:property|name)=[\\\"']og:image[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final byte[] PNG_HEADER = new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};

    private final SkinConfig config;
    private final SkinCache cache;
    private final MojangProfileResolver mojang = new MojangProfileResolver();
    private final HttpClient http;
    private final ExecutorService workers;
    private final Gson gson = new Gson();

    public SkinResolver(SkinConfig config) {
        this.config = config;
        this.cache = new SkinCache(config.cacheDirectory());
        this.http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        this.workers = Executors.newFixedThreadPool(2, runnable -> { Thread thread = new Thread(runnable,"Lively-Skin-Resolver"); thread.setDaemon(true); return thread; });
    }

    public CompletableFuture<GameProfile> resolve(MinecraftServer server, UUID npcId, String rawSource, String displayName) {
        SkinSource source = SkinSource.parse(rawSource);
        return switch (source.kind()) {
            case DEFAULT -> CompletableFuture.completedFuture(new GameProfile(npcId, safeProfileName(displayName)));
            case MOJANG -> mojang.resolve(server, npcId, source.value(), displayName);
            case TEXTURE -> CompletableFuture.completedFuture(profile(npcId, displayName,
                    new SkinCache.TextureData(source.value(), source.signature(), "classic", rawSource, Instant.now())));
            case URL -> CompletableFuture.supplyAsync(() -> profile(npcId, displayName, resolveUrl(source.value(), displayName)), workers);
            case MINESKIN -> CompletableFuture.supplyAsync(() -> profile(npcId, displayName, resolveMineSkinId(source.value())), workers);
        };
    }
    public void invalidate(String source) { cache.invalidate(source == null ? "" : source); }

    private SkinCache.TextureData resolveUrl(String rawUrl, String displayName) {
        return cache.get(rawUrl, config.cacheTtl()).orElseGet(() -> {
            URI image = resolveImageUri(rawUrl); validatePng(image); SkinCache.TextureData data;
            if (isTextureCdn(image)) data = unsignedTexture(image, rawUrl);
            else {
                if (!config.mineSkinEnabled() || config.mineSkinApiKey().isBlank())
                    throw new IllegalStateException("custom skin URL needs a signed texture; configure mineskin.api_key or use texture:<value>|<signature>");
                data = generateSignedTexture(image, displayName, rawUrl);
            }
            cache.put(rawUrl, data); return data;
        });
    }

    private SkinCache.TextureData resolveMineSkinId(String id) {
        String normalized=id.trim(); if(!normalized.matches("[A-Za-z0-9-]{8,64}"))throw new IllegalArgumentException("invalid MineSkin id");
        String cacheKey="mineskin:"+normalized;
        return cache.get(cacheKey,config.cacheTtl()).orElseGet(() -> {
            HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("https://api.mineskin.org/v2/skins/"+normalized)).timeout(config.requestTimeout())
                    .header("Accept","application/json").header("User-Agent",config.userAgent()).GET();
            if(!config.mineSkinApiKey().isBlank())request.header("Authorization","Bearer "+config.mineSkinApiKey());
            SkinCache.TextureData data=parseMineSkin(sendKnownService(request.build(),config.maxPageBytes()),cacheKey); cache.put(cacheKey,data); return data;
        });
    }

    private SkinCache.TextureData generateSignedTexture(URI image,String displayName,String source) {
        String form="url="+URLEncoder.encode(image.toString(),StandardCharsets.UTF_8)+"&visibility=1&name="+URLEncoder.encode(safeProfileName(displayName),StandardCharsets.UTF_8);
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("https://api.mineskin.org/generate/url?v2=true")).timeout(config.requestTimeout())
                .header("Accept","application/json").header("Content-Type","application/x-www-form-urlencoded").header("User-Agent",config.userAgent())
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if(!config.mineSkinApiKey().isBlank())request.header("Authorization","Bearer "+config.mineSkinApiKey());
        return parseMineSkin(sendKnownService(request.build(),config.maxPageBytes()),source);
    }

    private SkinCache.TextureData parseMineSkin(byte[] bytes,String source) {
        JsonObject root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(); JsonObject texture=null; String model="classic";
        if(root.has("skin")&&root.get("skin").isJsonObject()){
            JsonObject skin=root.getAsJsonObject("skin"); if(skin.has("variant"))model=skin.get("variant").getAsString();
            if(skin.has("texture")&&skin.get("texture").isJsonObject()){JsonObject t=skin.getAsJsonObject("texture");texture=t.has("data")&&t.get("data").isJsonObject()?t.getAsJsonObject("data"):t;}
        } else if(root.has("data")&&root.get("data").isJsonObject()){
            JsonObject data=root.getAsJsonObject("data"); if(data.has("texture")&&data.get("texture").isJsonObject())texture=data.getAsJsonObject("texture"); if(root.has("model"))model=root.get("model").getAsString();
        }
        if(texture==null||!texture.has("value"))throw new IllegalStateException(firstApiMessage(root).orElse("MineSkin response did not contain texture data"));
        String value=texture.get("value").getAsString(); String signature=texture.has("signature")&&!texture.get("signature").isJsonNull()?texture.get("signature").getAsString():"";
        return new SkinCache.TextureData(value,signature,model,source,Instant.now());
    }

    private URI resolveImageUri(String raw) {
        URI source=parseAndValidate(raw); if(isTextureCdn(source)||looksLikePng(source))return source;
        RemoteResponse response=fetch(source,config.maxPageBytes()); String type=response.contentType().toLowerCase(java.util.Locale.ROOT);
        if(type.contains("image/png")||isPng(response.body()))return response.finalUri();
        String html=new String(response.body(),StandardCharsets.UTF_8).replace("&amp;","&");
        Matcher texture=TEXTURE_URL.matcher(html); if(texture.find())return parseAndValidate(texture.group());
        Matcher png=PNG_URL.matcher(html); if(png.find())return parseAndValidate(png.group());
        Matcher og=OG_IMAGE.matcher(html); if(og.find())return parseAndValidate(response.finalUri().resolve(og.group(1)).toString());
        throw new IllegalArgumentException("skin page did not expose a usable PNG/texture URL");
    }

    private void validatePng(URI uri) {
        RemoteResponse response=fetch(uri,config.maxPngBytes()); if(!isPng(response.body()))throw new IllegalArgumentException("skin URL is not a PNG image");
        try { BufferedImage image=ImageIO.read(new ByteArrayInputStream(response.body())); if(image==null)throw new IllegalArgumentException("invalid PNG skin");
            boolean dimensions=image.getWidth()==64&&(image.getHeight()==64||image.getHeight()==32); if(!dimensions)throw new IllegalArgumentException("Minecraft skin must be 64x64 or legacy 64x32 PNG");
        } catch(IOException error){throw new IllegalArgumentException("cannot decode skin PNG",error);}
    }

    private SkinCache.TextureData unsignedTexture(URI image,String source) {
        JsonObject root=new JsonObject(); root.addProperty("timestamp",System.currentTimeMillis()); root.addProperty("signatureRequired",false);
        JsonObject textures=new JsonObject(),skin=new JsonObject();skin.addProperty("url",image.toString());textures.add("SKIN",skin);root.add("textures",textures);
        return new SkinCache.TextureData(Base64.getEncoder().encodeToString(gson.toJson(root).getBytes(StandardCharsets.UTF_8)),"","classic",source,Instant.now());
    }
    private GameProfile profile(UUID npcId,String displayName,SkinCache.TextureData texture) {
        GameProfile profile=new GameProfile(npcId,safeProfileName(displayName)); if(texture!=null&&texture.usable()){
            Property property=texture.signature().isBlank()?new Property("textures",texture.value()):new Property("textures",texture.value(),texture.signature()); profile.getProperties().put("textures",property);
        } return profile;
    }

    private RemoteResponse fetch(URI start,int maxBytes) {
        URI current=start;
        for(int redirects=0;redirects<=3;redirects++){
            current=parseAndValidate(current.toString());
            HttpRequest request=HttpRequest.newBuilder(current).timeout(config.requestTimeout()).header("Accept","image/png,text/html,application/xhtml+xml;q=0.9,*/*;q=0.1").header("User-Agent",config.userAgent()).GET().build();
            try {HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());int status=response.statusCode();
                if(status>=300&&status<400){response.body().close();current=current.resolve(response.headers().firstValue("location").orElseThrow(()->new IllegalArgumentException("redirect without location")));continue;}
                if(status<200||status>=300){response.body().close();throw new IllegalArgumentException("skin fetch HTTP "+status);} return new RemoteResponse(current,response.headers().firstValue("content-type").orElse(""),readLimited(response.body(),maxBytes));
            }catch(IOException error){throw new IllegalStateException("skin fetch failed",error);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("skin fetch interrupted",error);}
        }throw new IllegalArgumentException("too many skin URL redirects");
    }
    private byte[] sendKnownService(HttpRequest request,int maxBytes){try{HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());if(response.statusCode()<200||response.statusCode()>=300){byte[] error=readLimited(response.body(),Math.min(maxBytes,131072));throw new IllegalStateException("skin service HTTP "+response.statusCode()+": "+new String(error,StandardCharsets.UTF_8));}return readLimited(response.body(),maxBytes);}catch(IOException error){throw new IllegalStateException("skin service request failed",error);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("skin service request interrupted",error);}}

    private URI parseAndValidate(String raw) {
        final URI uri;try{uri=URI.create(raw.trim());}catch(RuntimeException error){throw new IllegalArgumentException("invalid skin URL",error);}
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(java.util.Locale.ROOT);if(!scheme.equals("https")&&!(config.allowHttp()&&scheme.equals("http")))throw new IllegalArgumentException("skin URL must use HTTPS");
        if(uri.getUserInfo()!=null)throw new IllegalArgumentException("skin URL user-info is not allowed");int port=uri.getPort();if(port!=-1&&port!=443&&!(config.allowHttp()&&port==80))throw new IllegalArgumentException("custom skin URL ports are not allowed");
        String host=uri.getHost();if(host==null||host.isBlank())throw new IllegalArgumentException("skin URL has no host");if(!config.hostAllowed(host))throw new IllegalArgumentException("skin host is not allowlisted: "+host);validatePublicAddress(host);return uri;
    }
    private static void validatePublicAddress(String host){try{InetAddress[] addresses=InetAddress.getAllByName(host);if(addresses.length==0)throw new IllegalArgumentException("skin host did not resolve");for(InetAddress address:addresses){if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||uniqueLocalIpv6(address))throw new IllegalArgumentException("skin host resolves to a private/local address");}}catch(IOException error){throw new IllegalArgumentException("cannot resolve skin host",error);}}
    private static boolean uniqueLocalIpv6(InetAddress address){if(!(address instanceof Inet6Address))return false;byte first=address.getAddress()[0];return(first&0xFE)==0xFC;}
    private static byte[] readLimited(InputStream input,int maxBytes)throws IOException{try(InputStream stream=input;ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(maxBytes,65536))){byte[] buffer=new byte[8192];int total=0,read;while((read=stream.read(buffer))>=0){total+=read;if(total>maxBytes)throw new IOException("remote skin payload exceeds configured limit");out.write(buffer,0,read);}return out.toByteArray();}}
    private static boolean looksLikePng(URI uri){return uri.getPath()!=null&&uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png");}
    private static boolean isTextureCdn(URI uri){return uri.getHost()!=null&&uri.getHost().equalsIgnoreCase("textures.minecraft.net");}
    private static boolean isPng(byte[] data){if(data.length<PNG_HEADER.length)return false;for(int i=0;i<PNG_HEADER.length;i++)if(data[i]!=PNG_HEADER[i])return false;return true;}
    private static Optional<String> firstApiMessage(JsonObject root){for(String key:new String[]{"errors","messages","warnings"}){JsonElement element=root.get(key);if(element!=null&&element.isJsonArray()&&element.getAsJsonArray().size()>0){JsonElement first=element.getAsJsonArray().get(0);if(first.isJsonObject()&&first.getAsJsonObject().has("message"))return Optional.of(first.getAsJsonObject().get("message").getAsString());}}return Optional.empty();}
    private static String safeProfileName(String input){String cleaned=input==null?"LivelyNPC":input.replaceAll("[^A-Za-z0-9_]","_");if(cleaned.isBlank())cleaned="LivelyNPC";return cleaned.substring(0,Math.min(16,cleaned.length()));}
    @Override public void close(){workers.shutdownNow();}
    private record RemoteResponse(URI finalUri,String contentType,byte[] body){}
}
