package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.oracleClientFlows.flows.GetRandomDiceRollValues
import com.oracleClientStatesAndContracts.contracts.DiceRollContract
import com.oracleClientStatesAndContracts.states.DiceRollState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ******************
// * Roll Dice Flow *
// ******************

/**
 * This flow uses the DiceRoll oracle. It gets a new, randomly generated DiceRollState
 * associated with a specific turn tracker as well as a specific GameBoardState. This
 * DiceRollState is then used to facilitate the collection of resources and the advancement
 * of the game.
 */

@InitiatingFlow
@StartableByRPC
class RollDiceFlow(val gameBoardStateLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardStateLinearId)
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        val turnTrackerState = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                .state.data
        val turnTrackerStateLinearId = turnTrackerState.linearId

        val oracleLegalName = CordaX500Name("Oracle", "New York", "US")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleLegalName)!!.legalIdentities.single()

        val diceRoll = subFlow(GetRandomDiceRollValues(turnTrackerStateLinearId, gameBoardState.linearId, gameBoardState.players, oracle))
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tb = TransactionBuilder(notary)

        tb.addOutputState(DiceRollState(diceRoll))
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addCommand(DiceRollContract.Commands.RollDice(), gameBoardState.players.map { it.owningKey })

        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(RollDiceFlow::class)
open class RollDiceFlowResponder(internal val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {

                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(stx.coreTransaction.references.single())
                        .state.data

                val turnTrackerStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data

                val diceRollState = stx.coreTransaction.outputsOfType<DiceRollState>().single()

                if (diceRollState.turnTrackerUniqueIdentifier != turnTrackerState.linearId) {
                    throw FlowException("Only the current player may roll the dice.")
                }

                if (diceRollState.gameBoardStateUniqueIdentifier != gameBoardState.linearId) {
                    throw FlowException("The dice roll must have been generated for this game.")
                }

                val oracle = serviceHub.networkMapCache.getNodeByLegalName(CordaX500Name("Oracle", "New York", "US"))
                if (diceRollState.signedDataWithOracleCert.sig.by != oracle!!.legalIdentitiesAndCerts.first().certificate) {
                    throw FlowException("This dice roll was not generated by the oracle")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}