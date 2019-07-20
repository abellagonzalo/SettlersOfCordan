package com.contractsAndStates.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class TileSides @ConstructorForDeserialization constructor(
        val value: List<TileSide> = List(HexTile.SIDE_COUNT) { TileSide() }
) : TileSideLocator, CornerLocator, RoadLocator, NeighborLocator {

    init {
        require(value.size == HexTile.SIDE_COUNT) { "value.size cannot be ${value.size}" }
        val nonNullNeighbors = value.filter { it.neighbor != null }
        require(nonNullNeighbors.size == nonNullNeighbors.toSet().size) {
            "There should be no non-null duplicates in the neighbors list"
        }
        val nonNullRoads = value.filter { it.roadId != null }
        require(nonNullRoads.size == nonNullRoads.toSet().size) {
            "There should be no non-null duplicates in the roadIds list"
        }
    }

    override fun getSideOn(sideIndex: TileSideIndex) = value[sideIndex.value]
    override fun indexOf(tileSide: TileSide) = value.indexOf(tileSide).let {
        if (it < 0) null
        else TileSideIndex(it)
    }

    override fun getNeighborOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).neighbor
    fun indexOf(tileIndex: HexTileIndex) = value.map { it.neighbor }.indexOf(tileIndex).let {
        if (it < 0) null
        else TileSideIndex(it)
    }

    override fun getRoadIdOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).roadId
    override fun getRoadIds() = value.map { it.roadId }

    /**
     * The neighbor corners returned are ordered clockwise.
     */
    override fun getOverlappedCorners(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex)
            .zip(cornerIndex.getOverlappedCorners())
            .map { pair ->
                pair.first.let {
                    if (it == null) null
                    else AbsoluteCorner(it, pair.second)
                }
            }

    class Builder(
            private val value: MutableList<TileSide> = MutableList(HexTile.SIDE_COUNT) { TileSide() }
    ) : TileSideLocator, TileSideBuilder, CornerLocator, RoadLocator, RoadBuilder, NeighborLocator, NeighborBuilder {

        constructor(tileSides: TileSides) : this(tileSides.value.toMutableList())

        override fun getSideOn(sideIndex: TileSideIndex) = value[sideIndex.value]
        override fun indexOf(tileSide: TileSide) = value.indexOf(tileSide).let {
            if (it < 0) null
            else TileSideIndex(it)
        }

        override fun setSideOn(sideIndex: TileSideIndex, tileSide: TileSide) = apply {
            require(getNeighborOn(sideIndex).let { it == null || it == tileSide.neighbor }) {
                "You cannot replace an existing neighbor"
            }
            require(getRoadIdOn(sideIndex).let { it == null || it == tileSide.roadId }) {
                "You cannot build a new road on top of a current one"
            }
            value[sideIndex.value] = tileSide
        }

        /**
         * The neighbor corners returned are ordered clockwise.
         */
        override fun getOverlappedCorners(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex)
                .zip(cornerIndex.getOverlappedCorners())
                .map { pair ->
                    pair.first.let {
                        if (it == null) null
                        else AbsoluteCorner(it, pair.second)
                    }
                }

        override fun getNeighborOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).neighbor
        override fun setNeighborOn(sideIndex: TileSideIndex, neighbor: HexTileIndex) = apply {
            require(getNeighborOn(sideIndex).let { it == null || it == neighbor }) {
                "You cannot replace an existing neighbor"
            }
            setSideOn(sideIndex, getSideOn(sideIndex).copy(neighbor = neighbor))
        }

        override fun getRoadIdOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).roadId
        override fun getRoadIds() = value.map { it.roadId }
        override fun setRoadIdOn(sideIndex: TileSideIndex, roadId: UniqueIdentifier) = apply {
            require(getRoadIdOn(sideIndex).let { it == null || it == roadId }) {
                "You cannot build a new road on top of a current one"
            }
            setSideOn(sideIndex, getSideOn(sideIndex).copy(roadId = roadId))
        }

        fun build() = TileSides(ImmutableList(value).toMutableList())
    }
}

interface TileSideLocator {
    fun getSideOn(sideIndex: TileSideIndex): TileSide
    fun indexOf(tileSide: TileSide): TileSideIndex?
}

interface TileSideBuilder {
    fun setSideOn(sideIndex: TileSideIndex, tileSide: TileSide): TileSideBuilder
}

interface CornerLocator {
    fun getOverlappedCorners(cornerIndex: TileCornerIndex): List<AbsoluteCorner?>
}

interface RoadLocator {
    fun getRoadIdOn(sideIndex: TileSideIndex): UniqueIdentifier?
    fun hasRoadIdOn(sideIndex: TileSideIndex) = getRoadIdOn(sideIndex) != null
    fun getRoadIds(): List<UniqueIdentifier?>
    fun indexOf(roadId: UniqueIdentifier) = getRoadIds().indexOf(roadId).let {
        if (it < 0) null
        else TileSideIndex(it)
    }
}

interface RoadBuilder {
    fun setRoadIdOn(sideIndex: TileSideIndex, roadId: UniqueIdentifier): RoadBuilder
}

interface NeighborLocator {
    fun getNeighborOn(sideIndex: TileSideIndex): HexTileIndex?
    fun isNeighborOn(sideIndex: TileSideIndex, tileIndex: HexTileIndex) = getNeighborOn(sideIndex) == tileIndex
    fun hasNeighborOn(sideIndex: TileSideIndex) = getNeighborOn(sideIndex) != null
    /**
     * The neighbors returned are ordered as per the argument.
     */
    fun getNeighborsOn(sideIndices: Iterable<TileSideIndex>) = sideIndices.map { getNeighborOn(it) }

    /**
     * The neighbors returned are ordered clockwise.
     */
    fun getNeighborsOn(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex.getAdjacentSides())
}

interface NeighborBuilder {
    fun setNeighborOn(sideIndex: TileSideIndex, neighbor: HexTileIndex): NeighborBuilder
}