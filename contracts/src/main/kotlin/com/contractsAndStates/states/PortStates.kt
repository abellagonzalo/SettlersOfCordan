package com.contractsAndStates.states

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PlacedPorts @ConstructorForDeserialization constructor(val value: List<Port>) {

    init {
        require(value.size == PORT_COUNT) {
            "value.size cannot be ${value.size}"
        }
    }

    companion object {
        const val PORT_COUNT = 9
    }

    fun getPortAt(hexTile: HexTileIndex, tileCorner: TileCornerIndex) = value.single {
        it.accessPoints.any { accessPoint ->
            accessPoint.hexTileIndex == hexTile && accessPoint.hexTileCoordinate.contains(tileCorner)
        }
    }

    fun toBuilder() = Builder(
            value.map { it.portTile }.toMutableList(),
            value.map { it.accessPoints }.toMutableList())

    class Builder(
            val portTiles: MutableList<PortTile> = mutableListOf(),
            val accessPointsList: MutableList<List<AccessPoint>> = mutableListOf()) {

        companion object {
            fun createAllPorts() = Builder()
                    .add(PortTile(listOf(Sheep).mapOf(2), allResources.minus(Sheep).mapOf(1)))
                    .add(PortTile(listOf(Wood).mapOf(2), allResources.minus(Wood).mapOf(1)))
                    .add(PortTile(listOf(Brick).mapOf(2), allResources.minus(Brick).mapOf(1)))
                    .add(PortTile(listOf(Ore).mapOf(2), allResources.minus(Ore).mapOf(1)))
                    .add(PortTile(listOf(Wheat).mapOf(2), allResources.minus(Wheat).mapOf(1)))
                    .add(PortTile(allResources.mapOf(3), allResources.mapOf(1)))
                    .add(PortTile(allResources.mapOf(3), allResources.mapOf(1)))
                    .add(PortTile(allResources.mapOf(3), allResources.mapOf(1)))
                    .add(PortTile(allResources.mapOf(3), allResources.mapOf(1)))
                    .add(listOf(AccessPoint(0, listOf(5, 1))))
                    .add(listOf(AccessPoint(1, listOf(0, 2)), AccessPoint(2, listOf(5))))
                    .add(listOf(AccessPoint(2, listOf(2)), AccessPoint(6, listOf(0, 1))))
                    .add(listOf(AccessPoint(11, listOf(1, 2))))
                    .add(listOf(AccessPoint(15, listOf(2, 3)), AccessPoint(18, listOf(1))))
                    .add(listOf(AccessPoint(18, listOf(4)), AccessPoint(17, listOf(2, 3))))
                    .add(listOf(AccessPoint(16, listOf(3, 4))))
                    .add(listOf(AccessPoint(12, listOf(4, 5)), AccessPoint(7, listOf(3))))
                    .add(listOf(AccessPoint(3, listOf(4, 5)), AccessPoint(7, listOf(0))))
        }

        fun add(portTile: PortTile) = apply { portTiles.add(portTile) }
        fun add(accessPoints: List<AccessPoint>) = apply {
            require(accessPoints.isNotEmpty()) { "accessPoints must not be empty" }
            accessPointsList.add(ImmutableList(accessPoints))
        }

        fun build(): PlacedPorts {
            require(portTiles.size == accessPointsList.size) {
                "ports and accessPointsList must have the same size"
            }
            return PlacedPorts(portTiles.mapIndexed { index, portTile ->
                Port(portTile, accessPointsList[index].toMutableList())
            })
        }
    }
}

@CordaSerializable
data class Port @ConstructorForDeserialization constructor(val portTile: PortTile, val accessPoints: List<AccessPoint>) {

    init {
        require(accessPoints.isNotEmpty()) { "accessPoints must not be empty" }
    }
}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<TokenType>>, val outputRequired: List<Amount<TokenType>>) {

    init {
        require(inputRequired.isNotEmpty()) { "inputRequired must not be empty" }
        require(outputRequired.isNotEmpty()) { "outputRequired must not be empty" }
        val inputTypes = inputRequired.map { it.token }
        require(inputTypes.size == inputTypes.toSet().size) {
            "There should be no duplicates in the inputRequired list"
        }
        require(inputRequired.none { it.quantity == 0L }) {
            "No inputRequired should have a 0 quantity"
        }
        val outputTypes = outputRequired.map { it.token }
        require(outputTypes.size == outputTypes.toSet().size) {
            "There should be no duplicates in the outputRequired list"
        }
        require(outputRequired.none { it.quantity == 0L }) {
            "No outputRequired should have a 0 quantity"
        }
    }

    fun getInputOf(token: TokenType) = inputRequired.single { it.token == token }
    fun getOutputOf(token: TokenType) = outputRequired.single { it.token == token }
}

@CordaSerializable
data class AccessPoint @ConstructorForDeserialization constructor(
        val hexTileIndex: HexTileIndex,
        val hexTileCoordinate: List<TileCornerIndex>) {

    constructor(hexTileIndex: Int, hexTileCoordinate: List<Int>) : this(
            HexTileIndex(hexTileIndex),
            hexTileCoordinate.map { TileCornerIndex(it) })

    init {
        require(hexTileCoordinate.isNotEmpty()) { "hexTileCoordinate must not be empty" }
    }
}