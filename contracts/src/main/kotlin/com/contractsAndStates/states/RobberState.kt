package com.contractsAndStates.states

import com.contractsAndStates.contracts.RobberContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

@CordaSerializable
@BelongsToContract(RobberContract::class)
data class RobberState(
        override val gameBoardLinearId: UniqueIdentifier,
        val hexTileIndex: HexTileIndex,
        val players: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState, StatePersistable, HasGameBoardId {
    override val participants: List<AbstractParty> = players

    fun move(hexTileIndex: HexTileIndex) = copy(hexTileIndex = hexTileIndex)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is RobberSchemaV1 -> RobberSchemaV1.PersistentRobberState(
                    gameBoardLinearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(RobberSchemaV1)
    }
}

object RobberSchema

@CordaSerializable
object RobberSchemaV1 : MappedSchema(
        schemaFamily = RobberSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentRobberState::class.java)
) {
    @Entity
    @Table(
            name = "contract_robber_states",
            indexes = [
                Index(name = "${BelongsToGameBoard.columnName}_idx", columnList = BelongsToGameBoard.columnName)
            ])
    class PersistentRobberState(
            @Column(name = BelongsToGameBoard.columnName, nullable = false)
            var gameBoardLinearId: String
    ) : PersistentState(), StatePersistable
}
