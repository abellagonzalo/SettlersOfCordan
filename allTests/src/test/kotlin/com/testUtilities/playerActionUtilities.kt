package com.testUtilities

import com.contractsAndStates.states.*
import com.flows.*
import com.oracleClientStatesAndContracts.states.DiceRollState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun rollDiceThenGatherThenMaybeEndTurn(
        gameBoardLinearId: UniqueIdentifier,
        node: StartedMockNode,
        network: MockNetwork,
        endTurn: Boolean = true,
        diceRollState: DiceRollState? = null): ResourceCollectionSignedTransactions {

    // Roll the dice
    val futureWithDiceRoll: CordaFuture<SignedTransaction> = when (diceRollState) {
        null -> node.startFlow(RollDiceFlow(gameBoardLinearId))
        else -> node.startFlow(RollDiceFlow(gameBoardLinearId, diceRollState))
    }

    network.runNetwork()
    val stxWithDiceRoll = futureWithDiceRoll.getOrThrow()

    // Collect Resources
    val futureWithResources = node.startFlow(GatherResourcesFlow(gameBoardLinearId))
    network.runNetwork()
    val stxWithIssuedResources = futureWithResources.getOrThrow()

    // End Turn if applicable
    var stxWithEndedTurn: SignedTransaction? = null
    if (endTurn) {
        val futureWithEndedTurn = node.startFlow(EndTurnFlow(gameBoardLinearId))
        network.runNetwork()
        stxWithEndedTurn = futureWithEndedTurn.getOrThrow()
    }

    return ResourceCollectionSignedTransactions(stxWithDiceRoll, stxWithIssuedResources, stxWithEndedTurn)
}

fun placeAPieceFromASpecificNodeAndEndTurn(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>, gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>, initialSetupComplete: Boolean) {
    // Build an initial settlement by issuing a settlement state
    // and updating the current turn.
    if (gameState.hexTiles.get(HexTileIndex(testCoordinates[i].first)).resourceType == HexTileType.Desert) {
        if (testCoordinates[i].first > 9) {
            testCoordinates[i] = Pair(testCoordinates[i].first + 1, testCoordinates[i].second)
        } else {
            testCoordinates[i] = Pair(testCoordinates[i].first + 3, testCoordinates[i].second)
        }
    }

    val currPlayer = arrayOfAllPlayerNodesInOrder[i]

    if (initialSetupComplete) {
        // Build a settlement only
        val buildSettlementFlow = BuildSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during normal game play
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow(gameState.linearId))
        network.runNetwork()
    } else {
        // Build an initial settlement and road
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during initial setup phase
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow(gameState.linearId))
        network.runNetwork()
    }

}