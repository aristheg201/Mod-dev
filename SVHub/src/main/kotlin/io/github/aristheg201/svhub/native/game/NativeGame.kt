package io.github.aristheg201.svhub.native.game

import java.util.UUID

enum class NativeBotDifficulty { EASY, NORMAL, HARD }

data class NativeSeat(
    val id: String,
    val name: String,
    /** Legacy in-session bot switch. Managed bots intentionally leave this false. */
    val bot: Boolean = false,
    val managedBot: Boolean = false,
    val botDifficulty: NativeBotDifficulty? = if (bot || managedBot) NativeBotDifficulty.NORMAL else null
) {
    val anyBot: Boolean get() = bot || managedBot
}

data class NativeActionView(val id:String,val label:String,val hint:String="",val enabled:Boolean=true,val payload:Map<String,String> = emptyMap())
data class NativeCardView(val id:String,val label:String,val subtitle:String="",val accent:String="neutral",val value:Int=0,val meta:Map<String,String> = emptyMap())
data class NativeGameView(val sessionId:String,val gameId:String,val title:String,val phase:String,val turn:String="",val status:String="",val boardWidth:Int=0,val boardHeight:Int=0,val board:List<String> = emptyList(),val cards:List<NativeCardView> = emptyList(),val actions:List<NativeActionView> = emptyList(),val fields:Map<String,String> = emptyMap(),val log:List<String> = emptyList(),val revision:Long=0L,val finished:Boolean=false,val winner:String?=null)
data class NativeGameResult(val accepted:Boolean,val changed:Boolean=false,val message:String="")
interface NativeGameSession{val sessionId:String;val gameId:String;val seats:List<NativeSeat>;val finished:Boolean;val winnerSeatId:String?;fun viewFor(viewerId:String):NativeGameView;fun act(viewerId:String,action:String,args:Map<String,String>):NativeGameResult;fun tick(nowMillis:Long):Boolean=false}
object NativeIds{fun session(prefix:String):String="$prefix-${UUID.randomUUID()}"}
