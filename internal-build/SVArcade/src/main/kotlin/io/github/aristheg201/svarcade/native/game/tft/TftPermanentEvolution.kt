package io.github.aristheg201.svarcade.native.game.tft

object TftPermanentEvolution {
    data class Result(val unit:TftOwnedUnit, val evolvedFrom:String? = null)

    fun advance(unit:TftOwnedUnit, definitions:Map<String,TftUnitDefinition>):Result {
        val definition=definitions[unit.unitId] ?: return Result(unit)
        val evolution=definition.permanentEvolution ?: return Result(unit)
        val progressed=unit.copy(combatRounds=unit.combatRounds+1)
        if(progressed.combatRounds<evolution.afterCombats)return Result(progressed)
        val target=definitions[evolution.targetUnit] ?: return Result(progressed)
        return Result(
            progressed.copy(
                unitId=target.id,
                combatRounds=0,
                poolUnitId=unit.poolSourceUnitId()
            ),
            evolvedFrom=unit.unitId
        )
    }
}
