package vn.svframe.svrtp;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Crash boundaries are explicit. Ambiguous transactions require reconciliation, never an automatic second credit. */
final class Journal {
    Map<UUID,Long> cooldowns=new HashMap<>();
    Map<UUID,Receipt> receipts=new HashMap<>();
    transient Path path;
    static final class Receipt {
        UUID id;String currency,amount,state;
        Receipt(String currency,String amount) { id=UUID.randomUUID();this.currency=currency;this.amount=amount;state="prepared"; }
    }
    static Journal load(Path path)throws IOException {
        Journal journal=Files.exists(path)?Config.JSON.fromJson(Files.readString(path),Journal.class):new Journal();
        if(journal==null || journal.cooldowns==null || journal.receipts==null)throw new IOException("Invalid RTP journal; refusing to overwrite");
        journal.path=path;return journal;
    }
    void save()throws IOException {
        cooldowns.entrySet().removeIf(e->e.getValue()<=System.currentTimeMillis());
        Files.createDirectories(path.getParent());
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            var data=java.nio.ByteBuffer.wrap(Config.JSON.toJson(this).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while(data.hasRemaining())channel.write(data);channel.force(true);
        }
        try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e) {Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
