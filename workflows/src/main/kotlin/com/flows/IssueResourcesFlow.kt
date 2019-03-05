package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.states.*
import com.oracleService.contracts.DiceRollContract
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.sdk.token.contracts.OwnedTokenAmountContract
import net.corda.sdk.token.contracts.commands.Issue
import net.corda.sdk.token.contracts.commands.OwnedTokenCommand
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import net.corda.sdk.token.contracts.types.Issued
import net.corda.sdk.token.contracts.utilities.AMOUNT
import net.corda.sdk.token.contracts.utilities.issuedBy

// *************************************
// * Initial Settlement Placement Flow *
// *************************************

@InitiatingFlow
@StartableByRPC
class IssueResourcesFlow(val gameBoardLinearId: UniqueIdentifier): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Get reference to the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()

        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService.queryBy<DiceRollState>().states.single()
        val diceRollState = diceRollStateAndRef.state.data

        // Step 5. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 6. Generate the appropriate resources
        val listOfValidSettlements = serviceHub.vaultService.queryBy<SettlementState>().states.filter { gameBoardState.hexTiles[it.state.data.hexTileCoordinate].roleTrigger == diceRollState.randomRoll1 + diceRollState.randomRoll2 }

        for (result in listOfValidSettlements) {
            val settlementState = result.state.data
            val hexTile = gameBoardState.hexTiles[result.state.data.hexTileCoordinate]
            val resourceType = hexTile.resourceType
            val gameCurrency = Resource.getInstance(resourceType)
            val resource = AMOUNT(settlementState.resourceAmountClaim, gameCurrency) issuedBy ourIdentity
            tb.addCommand(Issue(resource.token), gameBoardState.players.map { it.owningKey })
            tb.addOutputState(OwnedTokenAmount(resource, settlementState.owner))
        }

        // Step 7. Add reference states for turn tracker and game board. Add input state for the dice roll.
        tb.addInputState(diceRollStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(gameBoardStateAndRef))
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))

        // Step 8. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map {it.owningKey}
        tb.addCommand(GatherPhaseContract.Commands.issueResourcesToAllPlayers(), commandSigners)
        tb.addCommand(DiceRollContract.Commands.ConsumeDiceRoll(), commandSigners)
        tb.verify(serviceHub)

        // Step 9. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players + diceRollState.participants).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }

}
