package vn.svarcade.tcg.performance;
import java.nio.file.*;
import java.sql.*;
/** Disk-indexed unique receipts, bounded heap, FULL durable reservation before any external payout. */
public final class RewardReceiptJournal implements AutoCloseable {
 private final Connection db;
 public RewardReceiptJournal(Path path,Path legacy)throws Exception{Files.createDirectories(path.toAbsolutePath().getParent());db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());try(var s=db.createStatement()){s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000");s.execute("CREATE TABLE IF NOT EXISTS economy_reward_receipts(receipt TEXT PRIMARY KEY,state TEXT NOT NULL)");s.execute("CREATE TABLE IF NOT EXISTS economy_receipt_migrations(id TEXT PRIMARY KEY)");}migrate(legacy);}
 private void migrate(Path legacy)throws Exception{try(var q=db.prepareStatement("SELECT 1 FROM economy_receipt_migrations WHERE id=?")){q.setString(1,legacy.toAbsolutePath().toString());try(var r=q.executeQuery()){if(r.next())return;}}if(Files.exists(legacy)){db.setAutoCommit(false);try(var lines=Files.lines(legacy);var insert=db.prepareStatement("INSERT OR IGNORE INTO economy_reward_receipts VALUES(?,'PAID')")){var it=lines.iterator();int batch=0;while(it.hasNext()){String line=it.next();if(line.isBlank())continue;insert.setString(1,line.trim());insert.addBatch();if(++batch==256){insert.executeBatch();db.commit();batch=0;}}insert.executeBatch();db.commit();}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}}try(var q=db.prepareStatement("INSERT OR IGNORE INTO economy_receipt_migrations VALUES(?)")){q.setString(1,legacy.toAbsolutePath().toString());q.executeUpdate();}}
 public synchronized boolean payOnce(String receipt,Runnable payout){long started=CardWorldsPerfStats.start();try{
  try(var q=db.prepareStatement("SELECT state FROM economy_reward_receipts WHERE receipt=?")){q.setString(1,receipt);try(var r=q.executeQuery()){if(r.next()){if(r.getString(1).equals("PAID"))return false;throw new IllegalStateException("Unresolved economy reward receipt requires reconciliation: "+receipt);}}}
  try(var q=db.prepareStatement("INSERT INTO economy_reward_receipts VALUES(?,'PENDING')")){q.setString(1,receipt);q.executeUpdate();}
  // BEconomy's external balance store cannot participate in this SQLite transaction. An uncertain outcome
  // remains PENDING and is explicitly reported; replay never blindly duplicates an external payout.
  payout.run();
  try(var q=db.prepareStatement("UPDATE economy_reward_receipts SET state='PAID' WHERE receipt=?")){q.setString(1,receipt);q.executeUpdate();}return true;
 }catch(SQLException e){throw new IllegalStateException("Durable economy receipt failed: "+receipt,e);}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.DATABASE,started);}}
 @Override public synchronized void close()throws SQLException{db.close();}
}
